import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { ANALYZE_PROMPT, CHAT_PERSONA_PROMPT } from "./prompt";

admin.initializeApp();
const db = admin.firestore();

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const revenuecatWebhookToken = defineSecret("REVENUECAT_WEBHOOK_TOKEN");

const REGION = "southamerica-east1"; // São Paulo — menor latência para usuários BR
const GEMINI_MODEL = "gemini-2.5-flash";
const FREE_DAILY_PHOTOS = 1;
const PREMIUM_DAILY_PHOTOS = 15;
// ~6MB de base64 ≈ foto JPEG de 4.5MB. O app envia JPEG a 80% de qualidade.
const MAX_IMAGE_BASE64_LENGTH = 6_000_000;

/** Data de "hoje" no fuso do usuário brasileiro (quota reseta à meia-noite BRT). */
function todayInSaoPaulo(): string {
  // en-CA produz o formato YYYY-MM-DD, igual ao usado nas entidades do app
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

async function isPremiumUser(uid: string): Promise<boolean> {
  const snap = await db.doc(`users/${uid}`).get();
  return snap.get("isPremium") === true;
}

/**
 * Reserva 1 foto na quota diária do usuário (transacional — reinstalar o app
 * não reseta, e duas chamadas simultâneas não furam o limite).
 */
async function reservePhotoQuota(uid: string, premium: boolean): Promise<void> {
  const limit = premium ? PREMIUM_DAILY_PHOTOS : FREE_DAILY_PHOTOS;
  const usageRef = db.doc(`users/${uid}/usage/${todayInSaoPaulo()}`);

  await db.runTransaction(async (tx) => {
    const usage = await tx.get(usageRef);
    const count: number = usage.get("photoCount") ?? 0;
    if (count >= limit) {
      throw new HttpsError(
        "resource-exhausted",
        premium
          ? `Você atingiu o limite de ${PREMIUM_DAILY_PHOTOS} fotos hoje. Amanhã tem mais!`
          : "Você já usou sua foto grátis de hoje. Assine o Premium para analisar até 15 fotos por dia.",
        { isPremium: premium, limit }
      );
    }
    tx.set(
      usageRef,
      { photoCount: count + 1, updatedAt: admin.firestore.FieldValue.serverTimestamp() },
      { merge: true }
    );
  });
}

/** Devolve a foto reservada quando a análise falha (falha nossa ≠ quota gasta). */
async function refundPhotoQuota(uid: string): Promise<void> {
  const usageRef = db.doc(`users/${uid}/usage/${todayInSaoPaulo()}`);
  try {
    await usageRef.set(
      { photoCount: admin.firestore.FieldValue.increment(-1) },
      { merge: true }
    );
  } catch (e) {
    logger.warn(`Falha ao estornar quota de ${uid}`, e);
  }
}

interface FoodItem {
  name: string;
  preparo: string;
}

async function callGemini(imageBase64: string, apiKey: string): Promise<{ items: FoodItem[] }> {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
  const body = {
    contents: [
      {
        parts: [
          { text: "Analise este prato." },
          { inlineData: { mimeType: "image/jpeg", data: imageBase64 } },
        ],
      },
    ],
    systemInstruction: { parts: [{ text: ANALYZE_PROMPT }] },
    generationConfig: { responseMimeType: "application/json", temperature: 0.2 },
  };

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const errorText = await response.text();
    logger.error(`Gemini retornou ${response.status}: ${errorText}`);
    throw new HttpsError("internal", "O serviço de análise está indisponível. Tente novamente.");
  }

  const data = (await response.json()) as {
    candidates?: { content?: { parts?: { text?: string }[] } }[];
  };
  const text = data.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
  const cleanJson = text.replace(/```json/g, "").replace(/```/g, "").trim();

  let parsed: { items?: FoodItem[] };
  try {
    parsed = JSON.parse(cleanJson);
  } catch {
    logger.error(`Resposta do Gemini não é JSON válido: ${cleanJson.slice(0, 500)}`);
    throw new HttpsError("internal", "A análise retornou um formato inesperado. Tente novamente.");
  }

  const items = (parsed.items ?? []).filter(
    (item): item is FoodItem => typeof item?.name === "string" && typeof item?.preparo === "string"
  );
  return { items };
}

/**
 * Análise de foto de refeição. Chamada pelo app via Firebase Functions SDK.
 * Entrada: { imageBase64: string } · Saída: { items: [{ name, preparo }] }
 */
export const analyzePhoto = onCall(
  { region: REGION, secrets: [geminiApiKey], memory: "512MiB", timeoutSeconds: 120 },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Faça login para analisar fotos.");
    }

    const imageBase64: unknown = request.data?.imageBase64;
    if (typeof imageBase64 !== "string" || imageBase64.length === 0) {
      throw new HttpsError("invalid-argument", "imageBase64 é obrigatório.");
    }
    if (imageBase64.length > MAX_IMAGE_BASE64_LENGTH) {
      throw new HttpsError("invalid-argument", "Imagem grande demais. Envie um JPEG menor.");
    }

    const premium = await isPremiumUser(uid);
    await reservePhotoQuota(uid, premium);

    try {
      return await callGemini(imageBase64, geminiApiKey.value());
    } catch (e) {
      await refundPhotoQuota(uid);
      throw e;
    }
  }
);

type ChatTrigger = "user_message" | "meal_logged" | "daily_summary";
const CHAT_TRIGGERS: ChatTrigger[] = ["user_message", "meal_logged", "daily_summary"];
const MAX_CHAT_MESSAGE_LENGTH = 2000;
const MAX_HISTORY_MESSAGES = 10;

interface ChatHistoryTurn {
  role: "user" | "model";
  content: string;
}

interface ChatContext {
  profile?: { heightCm?: number; age?: number; sex?: string; activityLevel?: string; goal?: string };
  goals?: { calories?: number; protein?: number; carbs?: number; fat?: number };
  dailyTotals?: { kcal?: number; protein?: number; carbs?: number; fat?: number };
  loggedMeal?: { items?: { name: string; grams?: number; kcal?: number; protein?: number; carbs?: number; fat?: number }[] };
}

/** Monta o bloco de contexto (perfil/metas/consumo do dia) injetado no system instruction. */
function buildContextText(context: ChatContext): string {
  const lines: string[] = [];

  const p = context.profile;
  if (p) {
    lines.push(
      `Perfil do usuário: ${p.age ?? "?"} anos, ${p.heightCm ?? "?"}cm, sexo ${p.sex ?? "?"}, ` +
        `nível de atividade "${p.activityLevel ?? "?"}", objetivo "${p.goal ?? "?"}".`
    );
  }

  const g = context.goals;
  if (g) {
    lines.push(
      `Metas diárias: ${g.calories ?? "?"} kcal, proteína ${g.protein ?? "?"}g, ` +
        `carboidratos ${g.carbs ?? "?"}g, gordura ${g.fat ?? "?"}g.`
    );
  }

  const d = context.dailyTotals;
  if (d) {
    lines.push(
      `Consumido hoje até agora: ${d.kcal ?? 0} kcal, proteína ${d.protein ?? 0}g, ` +
        `carboidratos ${d.carbs ?? 0}g, gordura ${d.fat ?? 0}g.`
    );
  }

  const meal = context.loggedMeal?.items;
  if (meal && meal.length > 0) {
    const itemsText = meal
      .map((i) => `${i.name} (${i.grams ?? "?"}g, ${i.kcal ?? "?"}kcal)`)
      .join(", ");
    lines.push(`Refeição que o usuário acabou de registrar: ${itemsText}.`);
  }

  return lines.length > 0 ? lines.join("\n") : "Sem contexto adicional disponível.";
}

/** Turno sintético (não digitado pelo usuário) para gatilhos automáticos. */
function syntheticUserTurn(trigger: ChatTrigger): string {
  switch (trigger) {
    case "meal_logged":
      return "Acabei de registrar uma refeição. Comente brevemente considerando minhas metas e o que já comi hoje.";
    case "daily_summary":
      return "Gere um resumo breve e encorajador do meu dia com base no meu consumo total e minhas metas.";
    default:
      return "";
  }
}

async function callGeminiChat(
  systemInstruction: string,
  contents: { role: "user" | "model"; parts: { text: string }[] }[],
  apiKey: string
): Promise<string> {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
  const body = {
    contents,
    systemInstruction: { parts: [{ text: systemInstruction }] },
    generationConfig: { temperature: 0.6 },
  };

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const errorText = await response.text();
    logger.error(`Gemini (chat) retornou ${response.status}: ${errorText}`);
    throw new HttpsError("internal", "A Nutri está indisponível agora. Tente novamente.");
  }

  const data = (await response.json()) as {
    candidates?: { content?: { parts?: { text?: string }[] } }[];
  };
  const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
  if (!text) {
    logger.error("Gemini (chat) retornou resposta vazia");
    throw new HttpsError("internal", "A Nutri não conseguiu responder agora. Tente de novo.");
  }
  return text;
}

/**
 * Chat com a Nutri IA. Três gatilhos:
 * - "user_message": pergunta livre digitada pelo usuário — exige Premium.
 * - "meal_logged": comentário automático pós-refeição — não exige Premium
 *   (já é naturalmente limitado pela quota de fotos do FREE).
 * - "daily_summary": resumo do dia — mesma regra de "meal_logged".
 *
 * Sem persistência de histórico no servidor: o app manda as últimas
 * mensagens no payload a cada chamada (Room local é a fonte da verdade).
 */
export const chat = onCall({ region: REGION, secrets: [geminiApiKey] }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Faça login para conversar com a Nutri.");
  }

  const trigger = request.data?.trigger as unknown;
  if (typeof trigger !== "string" || !CHAT_TRIGGERS.includes(trigger as ChatTrigger)) {
    throw new HttpsError("invalid-argument", "trigger inválido.");
  }

  if (trigger === "user_message") {
    const premium = await isPremiumUser(uid);
    if (!premium) {
      throw new HttpsError(
        "permission-denied",
        "O chat com a Nutri é exclusivo do Premium. Assine para conversar à vontade."
      );
    }
  }

  const context: ChatContext = (request.data?.context as ChatContext) ?? {};
  const contextText = buildContextText(context);
  const systemInstruction = `${CHAT_PERSONA_PROMPT}\n\nContexto atual do usuário:\n${contextText}`;

  const contents: { role: "user" | "model"; parts: { text: string }[] }[] = [];

  if (trigger === "user_message") {
    const message = request.data?.message as unknown;
    if (typeof message !== "string" || message.trim().length === 0) {
      throw new HttpsError("invalid-argument", "message é obrigatório para user_message.");
    }
    if (message.length > MAX_CHAT_MESSAGE_LENGTH) {
      throw new HttpsError("invalid-argument", "Mensagem muito longa.");
    }

    const rawHistory = Array.isArray(request.data?.history) ? (request.data.history as ChatHistoryTurn[]) : [];
    const history = rawHistory.slice(-MAX_HISTORY_MESSAGES).filter(
      (h): h is ChatHistoryTurn =>
        (h?.role === "user" || h?.role === "model") && typeof h?.content === "string"
    );

    for (const turn of history) {
      contents.push({ role: turn.role, parts: [{ text: turn.content }] });
    }
    contents.push({ role: "user", parts: [{ text: message }] });
  } else {
    contents.push({ role: "user", parts: [{ text: syntheticUserTurn(trigger as ChatTrigger) }] });
  }

  const reply = await callGeminiChat(systemInstruction, contents, geminiApiKey.value());
  return { reply };
});

/**
 * Webhook do RevenueCat: mantém users/{uid}.isPremium em dia no Firestore.
 * Configurar no painel do RevenueCat com o header:
 *   Authorization: Bearer <REVENUECAT_WEBHOOK_TOKEN>
 * O app_user_id do RevenueCat DEVE ser o uid do Firebase (configurado no SDK).
 */
export const revenuecatWebhook = onRequest(
  { region: REGION, secrets: [revenuecatWebhookToken] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method Not Allowed");
      return;
    }
    const authHeader = req.get("Authorization") ?? "";
    if (authHeader !== `Bearer ${revenuecatWebhookToken.value()}`) {
      logger.warn("Webhook RevenueCat com token inválido");
      res.status(401).send("Unauthorized");
      return;
    }

    const event = req.body?.event;
    const uid: string | undefined = event?.app_user_id;
    const type: string | undefined = event?.type;
    if (!uid || !type) {
      res.status(400).send("Bad Request");
      return;
    }

    const grantsPremium = ["INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE"];
    const revokesPremium = ["EXPIRATION"];

    let isPremium: boolean | null = null;
    if (grantsPremium.includes(type)) isPremium = true;
    if (revokesPremium.includes(type)) isPremium = false;

    if (isPremium !== null) {
      await db.doc(`users/${uid}`).set(
        {
          isPremium,
          subscriptionUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
          lastSubscriptionEvent: type,
        },
        { merge: true }
      );
      logger.info(`Assinatura de ${uid}: ${type} → isPremium=${isPremium}`);
    }

    res.status(200).send("OK");
  }
);

/**
 * Exclusão de conta (obrigatório pela Play Store — usuário precisa conseguir
 * apagar os próprios dados dentro do app). Apaga o documento users/{uid} e
 * todas as subcoleções (ex: usage/*) recursivamente, depois remove o usuário
 * do Firebase Auth. Irreversível.
 */
export const deleteAccount = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Faça login para excluir sua conta.");
  }

  await db.recursiveDelete(db.doc(`users/${uid}`));

  try {
    await admin.auth().deleteUser(uid);
  } catch (e) {
    logger.error(`Falha ao excluir usuário ${uid} do Firebase Auth`, e);
    throw new HttpsError("internal", "Não foi possível excluir sua conta. Tente novamente.");
  }

  logger.info(`Conta excluída: ${uid}`);
  return { success: true };
});
