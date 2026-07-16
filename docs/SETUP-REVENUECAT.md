# 💳 Setup do RevenueCat — Passos Manuais (Fase 2)

Este guia lista **exatamente o que só você (dono das contas) pode fazer**.
Todo o código já está pronto (SDK integrado, paywall, webhook de assinatura)
— ao completar estes passos, a compra real passa a funcionar.

> ⚠️ **Sequenciamento obrigatório**: a Play Console exige o app publicado em
> pelo menos Teste Interno antes de você conseguir criar produtos de
> assinatura, e o RevenueCat exige esses produtos existentes antes de montar
> a Offering. Siga a ordem abaixo.

---

## 1. Publicar o app em Teste Interno na Play Console

Se ainda não fez isso (parte da Fase 0 do [ROADMAP.md](../ROADMAP.md)):

1. Gere um AAB assinado: `./gradlew bundleRelease`
2. Play Console → seu app → **Teste → Teste interno** → criar uma versão → subir o AAB
3. Adicione seu e-mail como testador interno

## 2. Criar as assinaturas na Play Console

1. Play Console → seu app → **Monetizar → Produtos → Assinaturas**
2. Criar assinatura com ID `premium`
3. Adicionar dois **base plans**:
   - `monthly` — recorrência mensal — **R$ 24,90**
   - `annual` — recorrência anual — **R$ 149,90**
4. Em cada base plan, adicionar uma **oferta** com **7 dias de teste grátis**
   (free trial)
5. Ativar os base plans

## 3. Criar o projeto no RevenueCat

1. Acesse https://app.revenuecat.com/signup → criar conta/projeto (ex: "NutriMetric")
2. **Project Settings → Apps → Add App** → escolha **Google Play Store**
   - Package name: `br.com.nutrimetric.app`
   - Faça o upload da **service account JSON** do Google Play (RevenueCat tem
     um guia próprio para gerar essa credencial — necessária para o
     RevenueCat validar recibos de compra automaticamente)

## 4. Configurar Entitlement + Offering

1. **Entitlements** → criar um entitlement chamado exatamente `premium`
   (o app já espera esse nome — ver `SubscriptionRepository.kt`)
2. Anexar os produtos `premium:monthly` e `premium:annual` (criados no passo 2)
   a este entitlement
3. **Offerings** → criar (ou usar) a Offering `default` → **Current** →
   adicionar os dois pacotes (mensal e anual) — o app busca a "oferta atual"
   automaticamente, sem hardcode de IDs

## 5. Configurar a chave pública no app

1. RevenueCat → **Project Settings → API Keys** → copie a **Public app-specific
   API key** do app Android
2. Abra (ou crie) `local.properties` na raiz do projeto (já gitignorado) e
   adicione:
   ```properties
   REVENUECAT_API_KEY=goog_XXXXXXXXXXXXXXXXXXXXXXXXXXXX
   ```
3. Recompile — `BuildConfig.REVENUECAT_API_KEY` passa a ter o valor real

## 6. Conectar o webhook (já existe no backend — só falta o token real)

A Cloud Function `revenuecatWebhook` já está implantada desde a Fase 1, mas o
`REVENUECAT_WEBHOOK_TOKEN` configurado até agora era só um placeholder.

1. Gere um token aleatório forte (ex: `openssl rand -hex 32`)
2. Configure o **mesmo token** em dois lugares:
   ```bash
   firebase functions:secrets:set REVENUECAT_WEBHOOK_TOKEN
   # cole o token gerado quando pedir
   firebase deploy --only functions:revenuecatWebhook
   ```
3. No RevenueCat → **Project Settings → Integrations → Webhooks** → adicionar:
   - URL: a URL da function `revenuecatWebhook` (visível no console do
     Firebase, região `southamerica-east1`)
   - Authorization header: `Bearer <o mesmo token gerado acima>`

## 7. Testar com license tester (compra sandbox, sem cobrança real)

1. Play Console → **Configurações → Testes de licença** → adicione seu e-mail
   Google como license tester
2. No dispositivo/emulador logado com essa conta, abra o app → estoure a foto
   grátis do dia (ou vá em Configurações) → toque em "Ver planos Premium" /
   card "NutriMetric Premium"
3. Compre um dos planos — não é cobrado de verdade (license tester)
4. Confirme:
   - O app mostra `isPremium = true` quase imediatamente (listener do
     RevenueCat)
   - `users/{uid}.isPremium` vira `true` no Firestore (via webhook)
   - O limite de fotos diárias vira 15
5. Teste também **Restaurar compras** e o fluxo de logout/login com outra
   conta (não deve herdar o status premium)

---

Quando o passo 7 estiver verde, me avise: começo a **Fase 3 (Nutri IA — chat
com contexto)**.
