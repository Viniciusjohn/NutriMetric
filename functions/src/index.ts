import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { ANALYZE_PROMPT, CHAT_PERSONA_PROMPT, WEEKLY_SUMMARY_INSTRUCTION } from "./prompt";
import {
  ChatContext,
  ChatTrigger,
  FREE_DAILY_AUTO_CHAT,
  MAX_CHAT_MESSAGE_LENGTH,
  buildContextText,
  isAutoTrigger,
  isTestPremiumEmail,
  isValidTrigger,
  photoLimitFor,
  requiresPremium,
  sanitizeContext,
  sanitizeHistory,
  syntheticUserTurn,
  todayInSaoPaulo,
} from "./logic";

admin.initializeApp();
const db = admin.firestore();

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const revenuecatWebhookToken = defineSecret("REVENUECAT_WEBHOOK_TOKEN");

const REGION = "southamerica-east1"; // São Paulo — menor latência para usuários BR
const GEMINI_MODEL = "gemini-2.5-flash";
// ~6MB de base64 ≈ foto JPEG de 4.5MB. O app envia JPEG a 80% de qualidade.
const MAX_IMAGE_BASE64_LENGTH = 6_000_000;

async function isPremiumUser(uid: string): Promise<boolean> {
  const snap = await db.doc(`users/${uid}`).get();
  return snap.get("isPremium") === true;
}

/** Marca a última atividade do usuário — usado pela push de re-engajamento para saber quem sumiu. */
async function touchLastActivity(uid: string): Promise<void> {
  try {
    await db.doc(`users/${uid}`).set(
      { lastActivityAt: admin.firestore.FieldValue.serverTimestamp() },
      { merge: true }
    );
  } catch (e) {
    logger.warn(`Falha ao atualizar lastActivityAt de ${uid}`, e);
  }
}

/**
 * Reserva 1 foto na quota diária do usuário (transacional — reinstalar o app
 * não reseta, e duas chamadas simultâneas não furam o limite).
 */
async function reservePhotoQuota(uid: string, premium: boolean): Promise<void> {
  const limit = photoLimitFor(premium);
  const usageRef = db.doc(`users/${uid}/usage/${todayInSaoPaulo()}`);

  await db.runTransaction(async (tx) => {
    const usage = await tx.get(usageRef);
    const count: number = usage.get("photoCount") ?? 0;
    if (count >= limit) {
      throw new HttpsError(
        "resource-exhausted",
        premium
          ? `Você atingiu o limite de ${limit} fotos hoje. Amanhã tem mais!`
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

/**
 * Reserva 1 mensagem automática da Nutri (meal_logged/daily_summary) contra o
 * teto diário do FREE. Anti-abuso: sem isso, um cliente FREE poderia chamar a
 * function `chat` com esses triggers em loop e gerar chamadas Gemini
 * ilimitadas de graça. Transacional, mesmo doc de quota da foto.
 */
async function reserveAutoChatQuota(uid: string): Promise<void> {
  const usageRef = db.doc(`users/${uid}/usage/${todayInSaoPaulo()}`);

  await db.runTransaction(async (tx) => {
    const usage = await tx.get(usageRef);
    const count: number = usage.get("autoChatCount") ?? 0;
    if (count >= FREE_DAILY_AUTO_CHAT) {
      throw new HttpsError(
        "resource-exhausted",
        "Você atingiu o limite de mensagens automáticas da Nutri hoje. Assine o Premium para conversar à vontade."
      );
    }
    tx.set(
      usageRef,
      { autoChatCount: count + 1, updatedAt: admin.firestore.FieldValue.serverTimestamp() },
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
    await touchLastActivity(uid);

    try {
      return await callGemini(imageBase64, geminiApiKey.value());
    } catch (e) {
      await refundPhotoQuota(uid);
      throw e;
    }
  }
);


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

  const rawTrigger = request.data?.trigger as unknown;
  if (!isValidTrigger(rawTrigger)) {
    throw new HttpsError("invalid-argument", "trigger inválido.");
  }
  const trigger: ChatTrigger = rawTrigger;

  if (requiresPremium(trigger)) {
    const premium = await isPremiumUser(uid);
    if (!premium) {
      throw new HttpsError(
        "permission-denied",
        trigger === "weekly_summary"
          ? "O relatório semanal com análise da Nutri é exclusivo do Premium."
          : "O chat com a Nutri é exclusivo do Premium. Assine para conversar à vontade."
      );
    }
  } else if (isAutoTrigger(trigger)) {
    // Gatilhos automáticos são grátis, mas para FREE contam contra o teto
    // diário — impede loop de chamadas Gemini de graça (anti-abuso de custo).
    const premium = await isPremiumUser(uid);
    if (!premium) {
      await reserveAutoChatQuota(uid);
    }
  }

  await touchLastActivity(uid);

  const context: ChatContext = sanitizeContext((request.data?.context as ChatContext) ?? {});
  const contextText = buildContextText(context);
  const extraInstruction = trigger === "weekly_summary" ? `\n\n${WEEKLY_SUMMARY_INSTRUCTION}` : "";
  const systemInstruction = `${CHAT_PERSONA_PROMPT}${extraInstruction}\n\nContexto atual do usuário:\n${contextText}`;

  const contents: { role: "user" | "model"; parts: { text: string }[] }[] = [];

  if (trigger === "user_message") {
    const message = request.data?.message as unknown;
    if (typeof message !== "string" || message.trim().length === 0) {
      throw new HttpsError("invalid-argument", "message é obrigatório para user_message.");
    }
    if (message.length > MAX_CHAT_MESSAGE_LENGTH) {
      throw new HttpsError("invalid-argument", "Mensagem muito longa.");
    }

    const history = sanitizeHistory(request.data?.history);

    for (const turn of history) {
      contents.push({ role: turn.role, parts: [{ text: turn.content }] });
    }
    contents.push({ role: "user", parts: [{ text: message }] });
  } else {
    contents.push({ role: "user", parts: [{ text: syntheticUserTurn(trigger) }] });
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

/**
 * Registra (ou atualiza) o token FCM do dispositivo do usuário logado.
 * Chamado pelo app sempre que o token muda (login, reinstalação, refresh).
 */
export const registerFcmToken = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Faça login para ativar notificações.");
  }

  const token: unknown = request.data?.token;
  if (typeof token !== "string" || token.length === 0) {
    throw new HttpsError("invalid-argument", "token é obrigatório.");
  }

  await db.doc(`users/${uid}`).set(
    { fcmToken: token, fcmTokenUpdatedAt: admin.firestore.FieldValue.serverTimestamp() },
    { merge: true }
  );

  return { success: true };
});

/**
 * Premium de TESTE: liga/desliga `users/{uid}.isPremium` para contas da
 * allowlist (TEST_PREMIUM_EMAILS). Serve pro dono testar as features premium
 * sem uma compra real do RevenueCat. A allowlist é checada NO SERVIDOR pelo
 * e-mail do token de auth — o cliente não consegue se auto-promover (as
 * Firestore Rules também bloqueiam escrever isPremium direto).
 */
async function setTestPremiumFor(uid: string, email: string | undefined, enable: boolean) {
  if (!isTestPremiumEmail(email)) {
    throw new HttpsError("permission-denied", "Conta não autorizada para Premium de teste.");
  }
  await db.doc(`users/${uid}`).set(
    {
      isPremium: enable,
      isTestPremium: enable,
      testPremiumUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
  logger.info(`Premium de teste de ${uid} (${email}) → ${enable}`);
  return { success: true, isPremium: enable };
}

export const setTestPremium = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Faça login.");
  return setTestPremiumFor(uid, request.auth?.token?.email, true);
});

export const clearTestPremium = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Faça login.");
  return setTestPremiumFor(uid, request.auth?.token?.email, false);
});

const REENGAGEMENT_INACTIVE_DAYS = 2;
const REENGAGEMENT_COOLDOWN_DAYS = 7;
const REENGAGEMENT_BATCH_LIMIT = 500;

/**
 * Push diária de re-engajamento: usuários com token FCM salvo e sem atividade
 * (foto analisada ou mensagem no chat) há 2+ dias recebem um lembrete, no
 * máximo uma vez a cada 7 dias por usuário (evita spam de quem já sumiu há meses).
 */
export const sendReengagementPush = onSchedule(
  { region: REGION, schedule: "every day 10:00", timeZone: "America/Sao_Paulo" },
  async () => {
    const cutoff = admin.firestore.Timestamp.fromMillis(
      Date.now() - REENGAGEMENT_INACTIVE_DAYS * 24 * 60 * 60 * 1000
    );
    const cooldownCutoff = admin.firestore.Timestamp.fromMillis(
      Date.now() - REENGAGEMENT_COOLDOWN_DAYS * 24 * 60 * 60 * 1000
    );

    const snap = await db
      .collection("users")
      .where("lastActivityAt", "<=", cutoff)
      .limit(REENGAGEMENT_BATCH_LIMIT)
      .get();

    let sent = 0;
    for (const doc of snap.docs) {
      const data = doc.data();
      const token: string | undefined = data.fcmToken;
      if (!token) continue;

      const lastPush = data.lastReengagementPushAt as admin.firestore.Timestamp | undefined;
      if (lastPush && lastPush.toMillis() > cooldownCutoff.toMillis()) continue;

      try {
        await admin.messaging().send({
          token,
          notification: {
            title: "A Nutri sentiu sua falta 🥗",
            body: "Que tal registrar sua próxima refeição? Leva só alguns segundos.",
          },
        });
        await doc.ref.set(
          { lastReengagementPushAt: admin.firestore.FieldValue.serverTimestamp() },
          { merge: true }
        );
        sent++;
      } catch (e) {
        logger.warn(`Falha ao enviar push de re-engajamento para ${doc.id}`, e);
      }
    }

    logger.info(`Push de re-engajamento: ${sent}/${snap.size} usuários notificados`);
  }
);
