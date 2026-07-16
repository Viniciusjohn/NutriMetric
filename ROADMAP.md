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

## FASE 3 — Nutri IA: o produto de verdade (~2–3 semanas)

**Meta: relacionamento diário. Onboarding conversacional → metas automáticas → chat com contexto.**

### 3.1 Perfil e onboarding conversacional
- **Novo:** `UserProfileEntity` (Room: peso, altura, idade, sexo, objetivo, atividade, restrições) + espelho no Firestore
- **Novo:** `ui/screens/OnboardingChatScreen.kt` — fluxo guiado em formato de conversa (perguntas fixas, não LLM — mais barato e confiável)
- Cálculo de metas: Mifflin-St Jeor (TMB) × fator de atividade ± objetivo → grava via `GoalsRepository` existente (DataStore) + Firestore
- Disclaimer LGPD + "não substitui nutricionista" com aceite explícito AQUI (dado de saúde = sensível)

### 3.2 Chat com a Nutri
- **Novos:** `ui/screens/ChatScreen.kt`, `ui/viewmodel/ChatViewModel.kt`, `repository/ChatRepository.kt`, `ChatMessageEntity` (Room, histórico local)
- Cloud Function `chat`: system prompt = persona NutriBR + perfil do usuário + consumo do dia (app envia totais do `DailyConsumptionEntity`) + últimas N mensagens
- **Gating:** FREE recebe só o feedback automático pós-foto; pergunta livre → paywall
- **Foto dentro do chat:** reusa `MainViewModel.analyzeImage()` + `TacoMatcher` + fluxo de salvar; após salvar, a function gera comentário ("Faltou proteína hoje...")
- Resumo de fim de dia: mensagem gerada no primeiro open após 20h (ou noti da Fase 4)

**✅ Milestone F3:** onboarding calcula metas sozinho; foto mandada no chat é analisada, salva e comentada pela Nutri.

---

## FASE 4 — Retenção (~1–2 semanas)

- **Lembretes de refeição:** WorkManager + notificações locais (horários configuráveis em `SettingsScreen`)
- **Streak 🔥:** calculado sobre datas do `DailyConsumptionEntity`; badge no `HomeScreen`
- **Push re-engajamento:** FCM (usuário sumiu 2+ dias → "A Nutri sentiu sua falta")
- **Relatório semanal:** nova aba/tela reusando Vico Charts + agregações do `HistoryScreen`; versão premium com análise escrita pela IA
- Permission `POST_NOTIFICATIONS` (Android 13+) via Accompanist já presente

**✅ Milestone F4:** lembretes disparando, streak visível, relatório semanal renderizando.

---

## FASE 5 — Lançamento na Play Store (~1–2 semanas + review)

### 5.1 Qualidade e observabilidade
- Crashlytics + Analytics com funil: `onboarding_complete`, `photo_analyzed`, `paywall_view`, `trial_start`, `purchase`
- Ligar `isMinifyEnabled = true` no release + regras ProGuard para Retrofit/Moshi/kotlinx-serialization/TFLite (hoje está desligado)
- Rodar `./gradlew test` (Robolectric/Roborazzi já configurados) + teste manual do fluxo completo

### 5.2 Compliance (bloqueia aprovação se faltar)
- **Política de privacidade LGPD** hospedada (dado de saúde = sensível: base legal, consentimento no onboarding)
- **Exclusão de conta**: obrigatório pela Play — fluxo in-app (Settings) + link web
- **Data Safety form** preenchido (coleta: saúde, fotos, identificadores)
- Disclaimer médico no app e na listing

### 5.3 Publicação
- Gerar AAB assinado (keystore da Fase 0 + Play App Signing)
- Listing PT-BR: título, descrição curta/longa, 8 screenshots, feature graphic
- **Trilha:** Interno (você) → **Fechado (12 testadores × 14 dias — requisito de conta nova, iniciar já na Fase 3!)** → Produção com rollout gradual (10% → 50% → 100%)

**✅ Milestone F5: app em produção na Play Store.** 🚀

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
