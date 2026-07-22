# 🛡️ Security Review pré-lançamento — NutriMetric

Auditoria da superfície sensível antes de expor o app a usuários reais e
receita real. Escopo: Cloud Functions (`functions/src/index.ts` + `logic.ts`),
regras do Firestore (`firestore.rules`), autenticação
(`repository/AuthRepository.kt`), assinatura
(`repository/SubscriptionRepository.kt`), e tratamento de segredos.

> Auditoria feita por leitura de código (não há emulador/pentest neste
> ambiente). Os itens marcados **CORRIGIDO** já têm o fix aplicado e coberto
> por teste (`functions/src/logic.test.ts`). Os **ACEITO/OK** foram avaliados e
> considerados adequados. Há um item que **exige validação sua no emulador**
> antes do deploy (regras do Firestore).

---

## Achados e resolução

### 1. [MÉDIO — CORRIGIDO] Abuso de custo: chat automático ilimitado no FREE
**Antes:** a function `chat` só exigia Premium para `user_message` e
`weekly_summary`. Os gatilhos automáticos `meal_logged` e `daily_summary` eram
liberados assumindo estarem "naturalmente limitados pela quota de 1 foto/dia do
FREE" — mas **nada no servidor verificava isso**. Um cliente FREE poderia
chamar `chat({trigger:"meal_logged", context:{...}})` em loop e gerar chamadas
Gemini ilimitadas de graça (custo direto pra você).

**Fix:** novo teto diário `FREE_DAILY_AUTO_CHAT = 6` (`logic.ts`) aplicado
transacionalmente via `reserveAutoChatQuota()` (`index.ts`) no mesmo doc de
quota (`users/{uid}/usage/{date}`, campo `autoChatCount`). Premium não tem
teto. Coberto por teste (gating premium vs. automático).

### 2. [MÉDIO/BAIXO — CORRIGIDO] Inflação de prompt via arrays de contexto
**Antes:** os arrays `weeklyTotals` e `loggedMeal.items` do payload do chat
iam direto pro prompt sem limite de tamanho — um cliente malicioso mandaria
arrays gigantes pra inflar o custo da chamada Gemini.

**Fix:** `sanitizeContext()` (`logic.ts`) corta ambos para
`MAX_CONTEXT_ARRAY_ITEMS = 31` (mantendo os mais recentes) antes de montar o
prompt. `sanitizeHistory()` reforça o corte do histórico em 10 turnos e filtra
papéis inválidos. Coberto por teste.

### 3. [BAIXO — CORRIGIDO] Regras do Firestore: escrita de campos arbitrários
**Antes:** `allow update` só bloqueava as chaves de premium (blacklist). O
cliente podia gravar **qualquer outro campo** no próprio doc `users/{uid}`
(ex.: `fcmToken` falso, `lastActivityAt` no futuro pra escapar da push de
reengajamento, ou strings gigantes = abuso de armazenamento).

**Fix:** trocado para **whitelist** — cliente só pode gravar
`displayName`, `email`, `updatedAt` (exatamente o que o `AuthRepository`
escreve). Todo o resto (isPremium, quota, fcmToken, lastActivityAt) é escrito
apenas pelas Cloud Functions via Admin SDK, que ignora as regras.

---

## Avaliado e considerado adequado (sem mudança)

- **Auto-promoção a Premium: bloqueada.** `isPremium` só é escrito pelo webhook
  do RevenueCat (Admin SDK); as regras impedem o cliente de setá-lo. ✔
- **Quota de fotos: transacional e server-side.** `reservePhotoQuota` usa
  `runTransaction`, então reinstalar o app ou chamadas concorrentes não furam o
  limite. Estorno (`refundPhotoQuota`) só ocorre em falha nossa. ✔
- **Sem segredos no cliente.** A chave do Gemini vive só no Secret Manager
  (servidor). A chave do RevenueCat no BuildConfig é **pública por design**
  (client-safe). `google-services.json` e a keystore estão no `.gitignore`. ✔
- **Callables autenticados + input validado.** Todos checam `request.auth?.uid`;
  `analyzePhoto` limita a imagem a ~6MB, `chat` limita a mensagem a 2000 chars.
  ✔
- **Webhook do RevenueCat.** Autentica por `Authorization: Bearer <token>` com
  o segredo do Secret Manager. A comparação de string não é constant-time, mas
  o token é de alta entropia e o ataque de timing é impraticável na prática —
  **risco aceito**. (Melhoria futura opcional: comparação constant-time.)
- **`deleteAccount` / `registerFcmToken`.** Ambos autenticados; o delete é
  recursivo (Firestore) + remove do Auth. ✔

---

## ⚠️ Ação recomendada antes do deploy (você, no emulador)

Não consigo rodar o emulador do Firestore aqui. Antes de publicar, rode
`firebase emulators:start --only firestore` e valide que as novas regras
(whitelist) permitem o fluxo legítimo do app:
- ✅ criar `users/{uid}` com `{displayName, email, updatedAt}` deve passar;
- ❌ update incluindo `isPremium` (ou qualquer campo fora da whitelist) deve
  ser negado;
- ✅ o app real continua logando e registrando refeições normalmente (o
  `ensureUserDocument` só toca os 3 campos permitidos).

Se você mexer no `AuthRepository.ensureUserDocument` pra gravar mais algum
campo no futuro, lembre de adicioná-lo à `clientWritableKeys()` nas regras.

---

## Verificação desta rodada
- `functions/`: `npx tsc --noEmit` ✔ · `npm test` (vitest) 15/15 ✔ ·
  `npm run build` gera `lib/` sem incluir os testes ✔.
- Regras do Firestore: revisão manual (validação no emulador fica com você,
  acima).
