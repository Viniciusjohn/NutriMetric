/**
 * Lógica pura das Cloud Functions — sem dependência do firebase-admin, para
 * poder ser testada isoladamente (functions/src/logic.test.ts). O index.ts
 * importa daqui; tudo que toca Firestore/Auth fica lá.
 */

// ---------- Premium de teste ----------
// Contas autorizadas a ligar/desligar o "Premium de teste" (setTestPremium).
// Em minúsculas; a checagem normaliza o e-mail do token. Manter curto.
export const TEST_PREMIUM_EMAILS: string[] = ["vinijohn00@gmail.com"];

/** True se o e-mail (do token de auth) pode usar o Premium de teste. */
export function isTestPremiumEmail(email: string | undefined | null): boolean {
  return typeof email === "string" && TEST_PREMIUM_EMAILS.includes(email.toLowerCase());
}

// ---------- Quotas ----------
export const FREE_DAILY_PHOTOS = 1;
export const PREMIUM_DAILY_PHOTOS = 15;
// Teto diário de mensagens automáticas da Nutri (meal_logged/daily_summary)
// para usuários FREE. Sem isso, um cliente FREE poderia chamar a function
// `chat` com esses triggers em loop e gerar chamadas Gemini ilimitadas de
// graça (o "naturalmente limitado pela quota de foto" não era verificado no
// servidor). Premium não tem esse teto.
export const FREE_DAILY_AUTO_CHAT = 6;

/** Limite de fotos do dia conforme o plano. */
export function photoLimitFor(premium: boolean): number {
  return premium ? PREMIUM_DAILY_PHOTOS : FREE_DAILY_PHOTOS;
}

/** Data de "hoje" no fuso do usuário brasileiro (quota reseta à meia-noite BRT). */
export function todayInSaoPaulo(now: Date = new Date()): string {
  // en-CA produz o formato YYYY-MM-DD, igual ao usado nas entidades do app
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(now);
}

// ---------- Chat ----------
export type ChatTrigger = "user_message" | "meal_logged" | "daily_summary" | "weekly_summary";
export const CHAT_TRIGGERS: ChatTrigger[] = ["user_message", "meal_logged", "daily_summary", "weekly_summary"];
// Gatilhos que exigem Premium: pergunta livre e o relatório semanal.
export const PREMIUM_ONLY_TRIGGERS: ChatTrigger[] = ["user_message", "weekly_summary"];
// Gatilhos automáticos: não exigem Premium, mas para FREE contam contra o
// teto diário FREE_DAILY_AUTO_CHAT (anti-abuso de custo).
export const AUTO_CHAT_TRIGGERS: ChatTrigger[] = ["meal_logged", "daily_summary"];

export const MAX_CHAT_MESSAGE_LENGTH = 2000;
export const MAX_HISTORY_MESSAGES = 10;
// Corte defensivo dos arrays de contexto vindos do cliente, para um cliente
// malicioso não inflar o prompt (custo) mandando arrays gigantes.
export const MAX_CONTEXT_ARRAY_ITEMS = 31;

export function isValidTrigger(trigger: unknown): trigger is ChatTrigger {
  return typeof trigger === "string" && CHAT_TRIGGERS.includes(trigger as ChatTrigger);
}

export function requiresPremium(trigger: ChatTrigger): boolean {
  return PREMIUM_ONLY_TRIGGERS.includes(trigger);
}

export function isAutoTrigger(trigger: ChatTrigger): boolean {
  return AUTO_CHAT_TRIGGERS.includes(trigger);
}

export interface ChatHistoryTurn {
  role: "user" | "model";
  content: string;
}

export interface WeeklyDayTotal {
  date?: string;
  kcal?: number;
  protein?: number;
  carbs?: number;
  fat?: number;
}

export interface ChatContext {
  profile?: { heightCm?: number; age?: number; sex?: string; activityLevel?: string; goal?: string };
  goals?: { calories?: number; protein?: number; carbs?: number; fat?: number };
  dailyTotals?: { kcal?: number; protein?: number; carbs?: number; fat?: number };
  loggedMeal?: { items?: { name: string; grams?: number; kcal?: number; protein?: number; carbs?: number; fat?: number }[] };
  weeklyTotals?: WeeklyDayTotal[];
}

/**
 * Limita o tamanho dos arrays do contexto antes de montar o prompt, para não
 * deixar o cliente inflar o custo da chamada Gemini. Mantém só os últimos N
 * itens (que são os mais relevantes: refeição atual e dias mais recentes).
 */
export function sanitizeContext(context: ChatContext): ChatContext {
  const clamped: ChatContext = { ...context };
  if (Array.isArray(context.weeklyTotals)) {
    clamped.weeklyTotals = context.weeklyTotals.slice(-MAX_CONTEXT_ARRAY_ITEMS);
  }
  if (context.loggedMeal?.items && Array.isArray(context.loggedMeal.items)) {
    clamped.loggedMeal = { items: context.loggedMeal.items.slice(0, MAX_CONTEXT_ARRAY_ITEMS) };
  }
  return clamped;
}

/** Normaliza o histórico recebido do cliente: só papéis válidos, últimos N. */
export function sanitizeHistory(rawHistory: unknown): ChatHistoryTurn[] {
  if (!Array.isArray(rawHistory)) return [];
  return (rawHistory as ChatHistoryTurn[])
    .slice(-MAX_HISTORY_MESSAGES)
    .filter(
      (h): h is ChatHistoryTurn =>
        (h?.role === "user" || h?.role === "model") && typeof h?.content === "string"
    );
}

/** Monta o bloco de contexto (perfil/metas/consumo do dia) injetado no system instruction. */
export function buildContextText(context: ChatContext): string {
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

  const weekly = context.weeklyTotals;
  if (weekly && weekly.length > 0) {
    const daysText = weekly
      .map((d) => `${d.date ?? "?"}: ${d.kcal ?? 0}kcal, prot ${d.protein ?? 0}g, carb ${d.carbs ?? 0}g, gord ${d.fat ?? 0}g`)
      .join("; ");
    lines.push(`Totais dos últimos dias (mais antigo → mais recente): ${daysText}.`);
  }

  return lines.length > 0 ? lines.join("\n") : "Sem contexto adicional disponível.";
}

/** Turno sintético (não digitado pelo usuário) para gatilhos automáticos. */
export function syntheticUserTurn(trigger: ChatTrigger): string {
  switch (trigger) {
    case "meal_logged":
      return "Acabei de registrar uma refeição. Comente brevemente considerando minhas metas e o que já comi hoje.";
    case "daily_summary":
      return "Gere um resumo breve e encorajador do meu dia com base no meu consumo total e minhas metas.";
    case "weekly_summary":
      return "Gere uma análise da minha semana com base nos totais diários e minhas metas.";
    default:
      return "";
  }
}
