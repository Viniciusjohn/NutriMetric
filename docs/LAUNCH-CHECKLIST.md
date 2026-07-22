# ✅ Checklist de Lançamento — NutriMetric na Play Store

Este é o **checklist mestre** do que só você (dono das contas) pode fazer. Todo
o código, os textos e a chave de assinatura já estão prontos — cada passo abaixo
aponta para o artefato que preparei.

Legenda: 🔴 bloqueia tudo · 🟡 bloqueia a publicação · 🟢 recomendado

---

## PARTE A — Contas (Fase 0) 🔴
Nenhuma dessas contas existe ainda. Elas destravam todo o resto.

- [ ] **A1. Google Play Console** — criar conta de desenvolvedor (taxa única de
  US$ 25). ⚠️ Conta pessoal nova exige um **teste fechado com 12 testadores por
  14 dias** antes de poder publicar em produção — quanto antes criar, antes esse
  relógio começa a contar (ver Parte F).
- [ ] **A2. Firebase** — criar projeto e subir para o **plano Blaze**
  (pay-as-you-go; necessário para Cloud Functions e Cloud Scheduler). Configure
  um alerta de orçamento no Google Cloud (ex: R$ 100/mês). Passo a passo em
  `docs/SETUP-FIREBASE.md`.
- [ ] **A3. RevenueCat** — criar conta (grátis até US$ 2,5k/mês de receita).
  Configuração detalhada em `docs/SETUP-REVENUECAT.md`.

## PARTE B — Backend no ar 🔴
Depende de A2.

- [ ] **B1.** Seguir `docs/SETUP-FIREBASE.md` por completo: baixar o
  `google-services.json` para `app/`, habilitar login Google + registrar
  SHA-1, criar o Firestore em `southamerica-east1`, setar os secrets
  (`GEMINI_API_KEY`, `REVENUECAT_WEBHOOK_TOKEN`) e rodar o deploy:
  ```
  firebase deploy --only functions,firestore:rules
  ```
  Isso publica `analyzePhoto`, `chat`, `revenuecatWebhook`, `deleteAccount`,
  `registerFcmToken` e a agendada `sendReengagementPush` (cria o job no Cloud
  Scheduler automaticamente).
- [ ] **B2.** Conferir no console do Firebase que as 6 functions subiram sem
  erro e que o Crashlytics/Analytics apareceram (vão aparecer assim que o
  primeiro build com `google-services.json` rodar).

## PARTE C — Assinaturas (RevenueCat + Play Billing) 🟡
Depende de A1, A3 e de um primeiro build assinado (Parte D).

- [ ] **C1.** Seguir `docs/SETUP-REVENUECAT.md`: criar os produtos de
  assinatura na Play Console (`monthly` R$ 24,90 e `annual` R$ 149,90, ambos
  com trial de 7 dias), o entitlement `premium` e a offering `default` no
  RevenueCat, colar a chave pública em `local.properties` como
  `REVENUECAT_API_KEY`, e configurar o webhook apontando para a função
  `revenuecatWebhook` com o header `Authorization: Bearer <token>`.
- [ ] **C2.** Fazer uma compra de teste em sandbox (seu e-mail como testador de
  licença) e confirmar que o `isPremium` vira `true` no app.

## PARTE D — Build assinado (AAB) 🟡
A chave já foi gerada e te entreguei (arquivo `nutrimetric-upload-key.jks` +
instruções). Depende de B1 (precisa do `google-services.json`).

- [ ] **D1.** Guardar a keystore + senha em 2 lugares seguros (ver o arquivo
  `LEIA-keystore-instrucoes.txt` que te enviei). **Perder = nunca mais atualizar
  o app.**
- [ ] **D2.** Gerar o bundle assinado:
  ```
  export KEYSTORE_PATH=/caminho/para/nutrimetric-upload-key.jks
  export STORE_PASSWORD='<senha do arquivo de instruções>'
  export KEY_PASSWORD='<mesma senha>'
  ./gradlew bundleRelease
  ```
  Saída: `app/build/outputs/bundle/release/app-release.aab`.
  > O release já vem com **R8/minify ligado** (`isMinifyEnabled = true`) e as
  > regras ProGuard prontas em `app/proguard-rules.pro` — nada a configurar.
- [ ] **D3.** Ao subir na Play Console pela primeira vez, **ativar o Play App
  Signing** (rede de segurança oficial: o Google guarda a chave final e a sua
  vira só a chave de upload, resetável pelo suporte se você perder).

## PARTE E — Ficha da loja e compliance 🟡
Pode adiantar em paralelo às outras partes.

- [ ] **E1. Política de privacidade hospedada** — habilitar **GitHub Pages**:
  no GitHub, **Settings → Pages → Source: Deploy from a branch → branch `main`,
  pasta `/docs`**. Depois de ~1 min os arquivos ficam em:
  - `https://viniciusjohn.github.io/NutriMetric/privacy-policy.html`
  - `https://viniciusjohn.github.io/NutriMetric/terms.html`
  (⚠️ não consigo habilitar isso por API — é ação de admin do repo. Os arquivos
  HTML já estão prontos em `docs/`.)
- [ ] **E2. Ficha da loja** — copiar título/descrições de
  `docs/PLAY-STORE-LISTING.md` para a Play Console (Presença na loja → Página
  principal). Colar também o link da política de privacidade (E1).
- [ ] **E3. Data Safety form** — preencher usando o gabarito
  `docs/PLAY-DATA-SAFETY.md` (mapeia cada categoria para a resposta certa com
  base no que o app coleta de verdade).
- [ ] **E4. Screenshots + feature graphic** — capturar as 6 telas no device
  real (guia no fim de `docs/PLAY-STORE-LISTING.md`) e montar o gráfico de
  destaque 1024×500. Os screenshots saem naturalmente do teste da Parte F.
- [ ] **E5. Classificação etária** — responder o questionário IARC declarando
  que **não** é app para crianças (saúde + IA conversacional).

## PARTE F — Testes e publicação 🟡
Depende de tudo acima.

- [ ] **F1. Teste manual do fluxo completo** em device real: onboarding →
  metas calculadas → registrar foto → comentário automático da Nutri → pergunta
  livre (Premium) / paywall (FREE) → relatório semanal → excluir conta. É aqui
  que você tira os screenshots da Parte E4.
- [ ] **F2. Trilha Interna** — subir o AAB na trilha de teste interno (só você),
  validação rápida.
- [ ] **F3. Trilha Fechada — 12 testadores × 14 dias** (obrigatório para conta
  nova antes de produção). Convide 12 pessoas, mantenha por 14 dias corridos.
  ⚠️ **Comece isso o quanto antes** — é o passo mais demorado do lançamento.
- [ ] **F4. Produção** — depois do teste fechado aprovado, promover para
  produção com **rollout gradual** (10% → 50% → 100%), acompanhando
  Crashlytics/Analytics a cada etapa.

---

## Resumo do que EU já deixei pronto (não precisa fazer nada)
- ✅ Código das Fases 1–5 (backend sem chaves, quota no servidor, login,
  paywall, onboarding, chat IA, retenção/push, relatório semanal).
- ✅ Release com R8/minify + regras ProGuard (build `minifyReleaseWithR8`
  verificado).
- ✅ Funil de Analytics instrumentado (`onboarding_complete`, `photo_analyzed`,
  `paywall_view`, `trial_start`, `purchase`).
- ✅ Disclaimer médico no app + política de privacidade LGPD e termos.
- ✅ Keystore de upload gerada e entregue.
- ✅ Docs de setup: `SETUP-FIREBASE.md`, `SETUP-REVENUECAT.md`,
  `PLAY-STORE-LISTING.md`, `PLAY-DATA-SAFETY.md`, e este checklist.

## Ordem recomendada
A1 → A2 → A3 (contas) → B (backend) → D (primeiro AAB) → C (assinaturas com o
build) → E (loja, em paralelo) → **F3 o quanto antes** (relógio de 14 dias) →
F4 (produção).
