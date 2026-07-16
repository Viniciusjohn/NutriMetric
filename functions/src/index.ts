import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { ANALYZE_PROMPT } from "./prompt";

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

/**
 * Chat com a Nutri IA — stub da Fase 3. Já valida auth e entitlement para o
 * app poder integrar o gating de paywall desde já.
 */
export const chat = onCall({ region: REGION, secrets: [geminiApiKey] }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Faça login para conversar com a Nutri.");
  }
  const premium = await isPremiumUser(uid);
  if (!premium) {
    throw new HttpsError(
      "permission-denied",
      "O chat com a Nutri é exclusivo do Premium. Assine para conversar à vontade."
    );
  }
  throw new HttpsError("unimplemented", "A Nutri IA chega na Fase 3. Aguarde!");
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
