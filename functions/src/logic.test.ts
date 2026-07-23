import { describe, it, expect } from "vitest";
import {
  AUTO_CHAT_TRIGGERS,
  CHAT_TRIGGERS,
  ChatContext,
  FREE_DAILY_PHOTOS,
  MAX_CONTEXT_ARRAY_ITEMS,
  MAX_HISTORY_MESSAGES,
  PREMIUM_DAILY_PHOTOS,
  PREMIUM_ONLY_TRIGGERS,
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

describe("quota de fotos", () => {
  it("FREE tem 1 foto/dia, PREMIUM tem 15", () => {
    expect(photoLimitFor(false)).toBe(FREE_DAILY_PHOTOS);
    expect(photoLimitFor(true)).toBe(PREMIUM_DAILY_PHOTOS);
    expect(FREE_DAILY_PHOTOS).toBe(1);
    expect(PREMIUM_DAILY_PHOTOS).toBe(15);
  });
});

describe("gating de triggers do chat", () => {
  it("user_message e weekly_summary exigem premium", () => {
    expect(requiresPremium("user_message")).toBe(true);
    expect(requiresPremium("weekly_summary")).toBe(true);
  });

  it("meal_logged e daily_summary NÃO exigem premium (mas são automáticos)", () => {
    expect(requiresPremium("meal_logged")).toBe(false);
    expect(requiresPremium("daily_summary")).toBe(false);
    expect(isAutoTrigger("meal_logged")).toBe(true);
    expect(isAutoTrigger("daily_summary")).toBe(true);
  });

  it("triggers automáticos e premium-only são disjuntos e cobrem todos os triggers", () => {
    for (const t of AUTO_CHAT_TRIGGERS) {
      expect(PREMIUM_ONLY_TRIGGERS).not.toContain(t);
    }
    // Todo trigger válido é premium-only OU automático (nunca ambos, nunca nenhum).
    for (const t of CHAT_TRIGGERS) {
      const premium = PREMIUM_ONLY_TRIGGERS.includes(t);
      const auto = AUTO_CHAT_TRIGGERS.includes(t);
      expect(premium !== auto).toBe(true);
    }
  });

  it("isValidTrigger rejeita valores fora da lista e não-strings", () => {
    expect(isValidTrigger("user_message")).toBe(true);
    expect(isValidTrigger("weekly_summary")).toBe(true);
    expect(isValidTrigger("admin")).toBe(false);
    expect(isValidTrigger("")).toBe(false);
    expect(isValidTrigger(null)).toBe(false);
    expect(isValidTrigger(42)).toBe(false);
    expect(isValidTrigger(undefined)).toBe(false);
  });
});

describe("sanitizeContext — anti-abuso de custo", () => {
  it("corta weeklyTotals para no máximo MAX_CONTEXT_ARRAY_ITEMS (mantendo os mais recentes)", () => {
    const big = Array.from({ length: 500 }, (_, i) => ({ date: `2026-01-${i}`, kcal: i }));
    const out = sanitizeContext({ weeklyTotals: big });
    expect(out.weeklyTotals!.length).toBe(MAX_CONTEXT_ARRAY_ITEMS);
    // mantém a cauda (mais recentes)
    expect(out.weeklyTotals![out.weeklyTotals!.length - 1].kcal).toBe(499);
  });

  it("corta loggedMeal.items para no máximo MAX_CONTEXT_ARRAY_ITEMS", () => {
    const items = Array.from({ length: 200 }, (_, i) => ({ name: `item${i}` }));
    const out = sanitizeContext({ loggedMeal: { items } });
    expect(out.loggedMeal!.items!.length).toBe(MAX_CONTEXT_ARRAY_ITEMS);
  });

  it("não quebra com contexto vazio ou sem arrays", () => {
    expect(sanitizeContext({})).toEqual({});
    const ctx: ChatContext = { profile: { age: 30 } };
    expect(sanitizeContext(ctx)).toEqual(ctx);
  });
});

describe("sanitizeHistory", () => {
  it("mantém no máximo MAX_HISTORY_MESSAGES turnos, os mais recentes", () => {
    const hist = Array.from({ length: 50 }, (_, i) => ({ role: "user" as const, content: `m${i}` }));
    const out = sanitizeHistory(hist);
    expect(out.length).toBe(MAX_HISTORY_MESSAGES);
    expect(out[out.length - 1].content).toBe("m49");
  });

  it("filtra turnos com papel inválido ou content não-string", () => {
    const out = sanitizeHistory([
      { role: "user", content: "ok" },
      { role: "system", content: "injetado" },
      { role: "model", content: 123 },
      { role: "model", content: "resposta" },
    ]);
    expect(out).toEqual([
      { role: "user", content: "ok" },
      { role: "model", content: "resposta" },
    ]);
  });

  it("retorna [] para entrada não-array", () => {
    expect(sanitizeHistory(undefined)).toEqual([]);
    expect(sanitizeHistory("nope")).toEqual([]);
    expect(sanitizeHistory(null)).toEqual([]);
  });
});

describe("buildContextText", () => {
  it("sem contexto, retorna a mensagem padrão", () => {
    expect(buildContextText({})).toBe("Sem contexto adicional disponível.");
  });

  it("inclui perfil, metas e consumo quando presentes", () => {
    const text = buildContextText({
      profile: { age: 30, heightCm: 175, sex: "M", activityLevel: "moderate", goal: "lose" },
      goals: { calories: 2000, protein: 130, carbs: 220, fat: 65 },
      dailyTotals: { kcal: 500, protein: 30, carbs: 60, fat: 15 },
    });
    expect(text).toContain("30 anos");
    expect(text).toContain("2000 kcal");
    expect(text).toContain("Consumido hoje");
  });
});

describe("syntheticUserTurn", () => {
  it("gera turno para cada trigger automático e vazio para user_message", () => {
    expect(syntheticUserTurn("meal_logged")).toContain("registrar uma refeição");
    expect(syntheticUserTurn("daily_summary")).toContain("resumo");
    expect(syntheticUserTurn("weekly_summary")).toContain("semana");
    expect(syntheticUserTurn("user_message")).toBe("");
  });
});

describe("isTestPremiumEmail (allowlist do Premium de teste)", () => {
  it("aceita o e-mail autorizado, ignorando maiúsculas", () => {
    expect(isTestPremiumEmail("vinijohn00@gmail.com")).toBe(true);
    expect(isTestPremiumEmail("ViniJohn00@Gmail.com")).toBe(true);
  });
  it("rejeita e-mails fora da lista e valores vazios/nulos", () => {
    expect(isTestPremiumEmail("outro@gmail.com")).toBe(false);
    expect(isTestPremiumEmail(undefined)).toBe(false);
    expect(isTestPremiumEmail(null)).toBe(false);
    expect(isTestPremiumEmail("")).toBe(false);
  });
});

describe("todayInSaoPaulo", () => {
  it("formata YYYY-MM-DD no fuso de São Paulo", () => {
    // 2026-03-10 02:00 UTC = 2026-03-09 23:00 em São Paulo (UTC-3)
    const d = new Date("2026-03-10T02:00:00Z");
    expect(todayInSaoPaulo(d)).toBe("2026-03-09");
  });
});
