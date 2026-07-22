# 🗺️ Roadmap Estratégico — NutriMetric 2.0 (Android → Play Store)

## Contexto

O NutriMetric hoje é um app Android (Kotlin/Compose) funcional que analisa fotos de refeições com IA (Gemini/NVIDIA + TFLite local) e faz matching com a tabela TACO. O problema: **não gera receita e não pode gerar** no estado atual, porque:

1. As chaves de API (Gemini/NVIDIA) estão embutidas no APK via BuildConfig — extraíveis por qualquer um que descompile o app. Chave roubada = fatura explodindo.
2. Qualquer limite de uso no dispositivo (ex: 1 foto/dia) é burlável reinstalando o app.
3. Não existe conta de usuário, assinatura, nem paywall.

**Objetivo:** transformar o app em "Nutricionista de bolso" com modelo freemium (FREE: 1 foto/dia; PREMIUM R$ 24,90/mês ou R$ 149,90/ano com trial de 7 dias, 15 fotos/dia + chat com a Nutri IA) e publicar na Play Store.

**Estado real do código (verificado):**
- `applicationId = "com.aistudio.pratobr.xpqtz"`, `namespace = "com.example"` (gerados pelo AI Studio)
- Firebase BOM já declarado nas deps, mas **sem** `google-services.json` e sem plugin — Firebase não está conectado
- `ai-service.js` na raiz já é o esqueleto perfeito da Cloud Function `analyzePhoto` (prompt NutriBR + TACO em JSON estruturado)
- Pipeline atual: `MainViewModel.analyzeImage()` → `TfliteFoodClassifier` (≥70% confiança) → fallback `NvidiaRepository.analisarFoto()` → `TacoMatcher` → `PlateReviewScreen` → `MealRepository.saveMeal()`
- `GoalsRepository` (DataStore) e `DailyConsumptionEntity` (Room) já existem — serão o contexto da Nutri IA
- Release build sem minify (`isMinifyEnabled=false`)

---

## FASE 0 — Decisões e contas (sem código, ~1 semana em paralelo)

Pré-requisitos que travam tudo se não existirem:

| Item | Ação | Atenção |
|---|---|---|
| **Google Play Console** | Criar conta (US$ 25 único) | Conta pessoal nova exige **12 testadores por 14 dias** em teste fechado antes de produção — começar isso CEDO |
| **Firebase** | Criar projeto + plano Blaze (pay-as-you-go, necessário p/ Cloud Functions) | Configurar alerta de orçamento (ex: R$ 100/mês) |
| **RevenueCat** | Criar conta (grátis até US$ 2.5k MTR/mês) | Conectar à Play Console via service account |
| **applicationId definitivo** | Trocar `com.aistudio.pratobr.xpqtz` por um ID próprio (ex: `br.com.nutrimetric.app`) | **IRREVERSÍVEL após publicar** — decidir agora |
| **Keystore de release** | Gerar + ativar Play App Signing | Guardar backup fora do repo |
| **Domínio/página** | Página simples p/ política de privacidade (obrigatória) | Pode ser GitHub Pages |

---

## FASE 1 — Fundação: Backend + Login (~2–3 semanas)

**Meta: zero chaves no APK, quota no servidor, usuário logado.**

### 1.1 Conectar Firebase ao app
- Aplicar plugin `com.google.gms.google-services` em `build.gradle.kts` (root + app) e adicionar `google-services.json`
- Adicionar em `gradle/libs.versions.toml`: `firebase-auth`, `firebase-firestore`, `firebase-functions`, `firebase-crashlytics`, `firebase-analytics`, `firebase-messaging` (todas via BOM já existente)
- Renomear pacote `com.example` → pacote definitivo (fazer agora, antes de crescer o código)

### 1.2 Login com Google
- **Novo:** `ui/screens/LoginScreen.kt`, `repository/AuthRepository.kt` (Credential Manager + Google Sign-In → Firebase Auth)
- **Modificar:** `MainActivity.kt` — NavHost passa a ter gate de autenticação (`startDestination` condicional: login vs home)

### 1.3 Cloud Functions (novo diretório `/functions`, TypeScript)
- **`analyzePhoto`** (callable, exige auth):
  1. Valida usuário → lê quota do dia no Firestore (transação)
  2. FREE: 1 foto/dia · PREMIUM: 15 fotos/dia → excedeu = erro `resource-exhausted`
  3. Chama Gemini 2.5 Flash com o prompt consolidado (fundir o prompt do `ai-service.js` com o prompt anti-alucinação do `NvidiaRepository` — hoje existem DOIS prompts divergentes, unificar no servidor)
  4. Chave via **Secret Manager** (nunca no código)
  5. Incrementa contador e retorna JSON estruturado
- **`chat`** (Fase 3, criar stub agora)
- **Firestore schema:**
  - `users/{uid}`: perfil, metas, `isPremium`
  - `users/{uid}/usage/{yyyy-MM-dd}`: `photoCount`
- Security Rules: usuário só lê/escreve os próprios docs; `usage` e `isPremium` só escritos pelas functions/webhook

### 1.4 Refatorar o app para usar o backend
- **Modificar:** `repository/NvidiaRepository.kt` → vira `AnalysisRepository`: troca chamada direta Retrofit→Gemini/NVIDIA por chamada à function `analyzePhoto` (SDK `firebase-functions`). Manter `TfliteFoodClassifier` como caminho local (não consome quota)
- **Remover:** `GEMINI_API_KEY`/`NVIDIA_API_KEY` do BuildConfig e do Secrets Gradle Plugin; remover `RetrofitClient` endpoints Gemini/NVIDIA (manter só OpenFoodFacts, que é API pública sem chave)
- **Deletar/arquivar:** `ai-service.js` da raiz (migra pra `/functions`)
- Tratar erro de quota excedida → estado que a Fase 2 vai ligar ao paywall

**✅ Milestone F1:** analisar foto logado, APK descompilado sem nenhuma chave, reinstalar o app não reseta a quota.

---

## FASE 1.5 — Retenção Barata (✅ implementada)

Features de retenção 100% locais (não dependem de contas externas), feitas antes da monetização:
- **💧 Água**: `WaterEntity`/`WaterDao`, card na home com botões rápidos (200/500ml) e desfazer, meta configurável em Settings
- **⚖️ Peso**: `WeightEntity`/`WeightDao`, card com peso atual + variação e diálogo de registro
- **🔔 Lembrete diário**: WorkManager (`ReminderWorker`/`ReminderScheduler`), `POST_NOTIFICATIONS`, seção em Settings com Switch + TimePicker
- **🔥 Streak**: `StreakCalculator` (função pura + teste unitário), badge no topo da home
- AppDatabase: `version 4→5` (migração destrutiva; app sem usuários)

## FASE 1.6 — Confiança na IA (✅ implementada)

- **✏️ Editar refeição salva**: `MealDao.updateMeal`/`getMealById`, `PlateReviewScreen`/`PlateViewModel` ganharam modo edição (`editingMealId`), rota `edit_meal/{mealId}`, ícone de editar no `MealCard` e no card do `HistoryScreen`
- **🔍 Busca manual TACO**: liga a query `TacoDao.searchFoods()` (já existia, estava órfã) na `ManualEntryScreen` com autopreenchimento de macros
- **⭐ Favoritos/repetir**: `MealEntity.isFavorite` (AppDatabase `version 5→6`), `FavoritesRow` na home, ícones de estrela/repetir no `MealCard` — duplica uma refeição pro dia atual sem re-fotografar
- Nota: múltiplos itens por foto já funcionava (não era gap); micronutrientes e widget de tela inicial ficaram fora de escopo (adiados)

## FASE 2.5 — Compliance de Publicação (✅ implementada, antecipada da Fase 5)

- **🔐 Logout + Exclusão de conta**: Cloud Function `deleteAccount` (apaga Firestore recursivamente + Firebase Auth), `AuthRepository.deleteAccount()`, seção "Conta" em Settings
- **📄 Política de privacidade + Termos de uso**: `docs/privacy-policy.html`, `docs/terms.html`, linkados em Settings — pendente apenas habilitar GitHub Pages (passo manual, ver `docs/SETUP-FIREBASE.md`)
- **📊 Firebase Analytics + Crashlytics**: dependências e plugin adicionados (aplicado condicionalmente, mesmo padrão do `google-services`) — sem eventos de funil customizados ainda (aguardam Fase 2/3)

## FASE 2 — Monetização (✅ código implementado, falta setup manual)

**Meta: assinatura funcionando em sandbox, paywall no lugar certo.**

- **🐛 Fix crítico**: `QuotaExceededException` era engolida silenciosamente (virava `AnalysisState.Success(emptyList())`) — corrigido com o subtipo `AnalysisState.QuotaExceeded`, agora dispara o paywall de verdade
- **`repository/SubscriptionRepository.kt`**: integração com o SDK do RevenueCat (`com.revenuecat.purchases:purchases`), `isPremium: Flow<Boolean>` reativo, `getOffering()`/`purchasePackage()`/`restorePurchases()`/`logout()`
- **`ui/screens/PaywallScreen.kt`**: preços/pacotes vêm dinamicamente da Offering do RevenueCat (sem hardcode), gatilhos: quota estourada (`AnalysisScreen`), upsell em `SettingsScreen` e `HistoryScreen`
- **Webhook RevenueCat → Cloud Function** (`revenuecatWebhook`, já existia desde a Fase 1): grava `users/{uid}.isPremium` no Firestore — é isso que faz a quota 1 vs 15 valer no servidor, cliente nunca decide
- `AuthRepository.signOut()`/`deleteAccount()` chamam `Purchases.sharedInstance.logOut()` — sem isso a próxima conta no aparelho herdaria o status premium
- **Pendente (só o dono das contas pode fazer)**: publicar em Teste Interno, criar produtos na Play Console, configurar Entitlement/Offering no RevenueCat, colar a API key em `local.properties`, gerar o token real do webhook — passo a passo completo em [docs/SETUP-REVENUECAT.md](docs/SETUP-REVENUECAT.md)

**✅ Milestone F2:** comprar com license tester → `isPremium` fica `true` no app e no Firestore, quota vira 15; cancelar → só revoga na expiração (não no cancelamento).

---

## FASE 3 — Nutri IA: o produto de verdade (✅ implementada)

**Meta: relacionamento diário. Onboarding conversacional → metas automáticas → chat com contexto.**

### 3.1 Perfil e onboarding conversacional
- `repository/ProfileRepository.kt` (DataStore, mesmo padrão do `GoalsRepository`): altura, idade, sexo, atividade, objetivo, flag `onboardingCompleted`. Peso vai para `WeightEntity`/`WeightDao` já existente (mesma tabela do card de peso)
- `ui/screens/OnboardingScreen.kt`: perguntas fixas (não LLM) estilizadas como bolhas de conversa, terminando em resumo das metas + **checkbox de consentimento LGPD explícito**
- `utils/NutritionCalculator.kt`: Mifflin-St Jeor (TMB) × fator de atividade ± objetivo (±500 kcal), proteína 2g/kg, gordura 25% das calorias — coberto por `NutritionCalculatorTest.kt`
- Gate reativo (não detecção de "novo usuário"): `MainViewModel.onboardingCompleted` redireciona a `HomeScreen` para `"onboarding"` sempre que o perfil não estiver completo

### 3.2 Chat com a Nutri
- `ui/screens/ChatScreen.kt`, `ui/viewmodel/ChatViewModel.kt`, `repository/ChatRepository.kt`, `ChatMessageEntity`/`ChatMessageDao` (Room, histórico 100% local — sem sync no Firestore)
- Cloud Function `chat` (antes stub, agora real): persona "Nutri" + contexto (perfil/metas/consumo do dia) enviado no payload da requisição a cada chamada — sem leitura do Firestore nem persistência de histórico no servidor
- **Gating por trigger:** só `user_message` (pergunta livre) exige Premium; `meal_logged`/`daily_summary` (comentários automáticos) não exigem — já limitados pela quota de fotos do FREE
- **Foto dentro do chat:** reaproveita a navegação existente (câmera/galeria → `analysis` → `PlateReviewScreen`, sem duplicar o pipeline); o id da refeição salva volta ao chat via `savedStateHandle` do NavController, que dispara o comentário automático da Nutri
- Resumo de fim de dia (`trigger: "daily_summary"`): function já suporta, gatilho automático fica para uma rodada futura (ligado à Fase 4 de notificações)

**✅ Milestone F3:** onboarding calcula metas sozinho; foto mandada no chat é analisada, salva e comentada pela Nutri; pergunta livre funciona no Premium e leva ao paywall no FREE.

---

## FASE 4 — Retenção (✅ implementada)

- **Lembretes de refeição:** WorkManager + notificações locais (horários configuráveis em `SettingsScreen`) — feito na Fase 1.5
- **Streak 🔥:** calculado sobre datas do `DailyConsumptionEntity`; badge no `HomeScreen` — feito na Fase 1.5
- **Push re-engajamento:** `NutriFirebaseMessagingService` + `PushRepository` registram o token FCM (`registerFcmToken`); Cloud Function agendada `sendReengagementPush` (`onSchedule`, diária às 10h BRT) notifica quem ficou 2+ dias sem abrir o app (`lastActivityAt`, tocado em `analyzePhoto`/`chat`), com cooldown de 7 dias por usuário para não spammar quem já sumiu há meses
- **Relatório semanal:** `WeeklyReportScreen` (gráfico de calorias via Vico Charts + médias de macros vs. metas, últimos 7 dias) acessível pelo ícone no `HomeScreen`; análise escrita pela Nutri IA é exclusiva Premium (novo gatilho `weekly_summary` na function `chat`, gated no servidor igual ao `user_message`) — FREE vê estatísticas normalmente e recebe upsell pro paywall (`reason=weekly_report_locked`)
- Permission `POST_NOTIFICATIONS` (Android 13+) via Accompanist já presente

**✅ Milestone F4:** lembretes disparando, streak visível, relatório semanal renderizando, push de re-engajamento agendada.

---

## FASE 5 — Lançamento na Play Store (código/docs prontos ✅, falta setup manual)

Todo o trabalho de código, documentação e artefatos está feito. O que resta
exige contas/ações humanas irredutíveis — consolidado em `docs/LAUNCH-CHECKLIST.md`.

### 5.1 Qualidade e observabilidade (✅ implementada)
- Funil de Analytics (`repository/AnalyticsRepository.kt`): `onboarding_complete`, `photo_analyzed`, `paywall_view`, `trial_start`, `purchase` — instrumentados em `MainViewModel`, `PaywallScreen`, `SubscriptionRepository` (trial vs purchase distinguidos por `PeriodType`). Crashlytics já vinha da Fase 1.
- `isMinifyEnabled = true` no release + regras ProGuard (kotlinx.serialization, Moshi reflexivo, Retrofit, TFLite, RevenueCat, coroutines) em `app/proguard-rules.pro` — build `:app:minifyReleaseWithR8` verificado.
- `:app:testDebugUnitTest` passando (21/21). Teste manual do fluxo completo fica pro teste em device (Parte F do checklist).

### 5.2 Compliance (✅ implementada)
- **Política de privacidade LGPD** (`docs/privacy-policy.html`) atualizada para todos os dados coletados (saúde, fotos, chat, token FCM, eventos de analytics); **termos** em `docs/terms.html`. Falta só habilitar o GitHub Pages (passo manual, ver checklist).
- **Exclusão de conta** in-app já existe desde a Fase 2.5 (function `deleteAccount`).
- **Data Safety form** — gabarito completo em `docs/PLAY-DATA-SAFETY.md`.
- **Disclaimer médico** no onboarding, no login e agora também como banner fixo no `ChatScreen`.

### 5.3 Publicação (preparada ✅, execução manual pelo dono)
- **AAB assinado**: keystore de upload gerada e entregue ao dono (alias `upload`, o `signingConfigs.release` já a espera). `bundleRelease` documentado no checklist.
- **Listing PT-BR**: `docs/PLAY-STORE-LISTING.md` (título, descrições, categoria, guia de screenshots pra capturar no device real).
- **Trilha:** Interno → **Fechado (12 testadores × 14 dias — requisito de conta nova, começar cedo)** → Produção com rollout gradual (10% → 50% → 100%).

**⏳ Milestone F5:** tudo que é código/docs pronto; publicação depende das contas (Play Console, Firebase Blaze, RevenueCat) — ver `docs/LAUNCH-CHECKLIST.md`. 🚀

---

## FASE 6 (futuro, fora de escopo) — iOS via Kotlin Multiplatform
Só depois de validar que o Android vende. Camadas `data/` e `repository/` são as candidatas a compartilhar.

---

## ⚠️ Riscos e mitigações

| Risco | Mitigação |
|---|---|
| 12 testadores × 14 dias (conta pessoal nova) atrasa lançamento | Abrir teste fechado durante a Fase 3, não no fim |
| Custo Gemini descontrolado | Quota no servidor + Gemini Flash (barato) + alerta de billing no GCP |
| `applicationId` errado publicado | Decidir e trocar na Fase 0 — é permanente |
| Dois prompts divergentes (ai-service.js vs NvidiaRepository) | Unificar em UM prompt no servidor na Fase 1 |
| Review da Play rejeitar por dado de saúde | Disclaimer + privacy policy + Data Safety completos antes do submit |
| Migração de dados locais | App ainda sem usuários — migração destrutiva do Room é aceitável agora, não depois |

## 📅 Linha do tempo estimada (dev solo + Claude Code)

```
Semana  1     : F0 contas/decisões (paralelo com F1)
Semanas 1–3   : F1 Fundação (Firebase, login, functions, refactor)
Semanas 4–5   : F2 Monetização (RevenueCat, paywall)
Semanas 6–8   : F3 Nutri IA (chat, onboarding)  ← abrir teste fechado aqui
Semanas 9–10  : F4 Retenção (notificações, streak, relatório)
Semanas 11–12 : F5 Lançamento (compliance, listing, rollout)
```
**Total: ~3 meses até produção.**

## ✔️ Verificação por fase
- **F1:** analisar foto logado; `apkanalyzer`/descompilar APK → nenhuma chave; reinstalar → quota mantida; Firestore Rules testadas no emulador
- **F2:** compra sandbox (license tester) destrava quota/chat; webhook atualiza `isPremium`; cancelamento reverte
- **F3:** onboarding → metas corretas (validar contra calculadora TMB); foto no chat → refeição salva + comentário coerente com o consumo do dia
- **F4:** lembrete dispara no horário; streak incrementa/zera corretamente
- **F5:** `./gradlew test` verde; AAB release instala e roda com minify ligado; checklist de compliance completo
