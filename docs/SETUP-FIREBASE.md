# 🔧 Setup do Firebase — Passos Manuais (Fase 0/1)

Este guia lista **exatamente o que só você (dono da conta) pode fazer**. Todo o
código já está pronto — ao completar estes passos, o login e a análise por IA
passam a funcionar automaticamente.

> Custo: o plano Blaze é pay-as-you-go com cota gratuita generosa. Configure o
> alerta de orçamento (passo 2.4) e o custo inicial tende a ser ~R$ 0.

---

## 1. Criar o projeto Firebase (~10 min)

1. Acesse https://console.firebase.google.com → **Adicionar projeto**
2. Nome: `NutriMetric` (o ID gerado pode ser `nutrimetric-xxxxx`)
3. Google Analytics: **ativar** (vamos usar na Fase 5)

## 2. Configurar o projeto

1. **Adicionar app Android**:
   - Package name: `br.com.nutrimetric.app` (⚠️ exatamente este — é o
     `applicationId` do projeto)
   - Baixe o **`google-services.json`** e coloque em **`app/google-services.json`**
     (o arquivo está no `.gitignore` — não será commitado)
2. **Authentication** → Sign-in method → habilite **Google**
   - Em "Configurações do projeto → Geral", cadastre o **SHA-1 de debug** do seu
     computador (obrigatório para o login Google funcionar):
     ```bash
     ./gradlew signingReport   # copie o SHA-1 do variant debug
     ```
3. **Firestore Database** → Criar banco → modo **produção** → região
   `southamerica-east1` (São Paulo)
4. **Upgrade para o plano Blaze** (menu ⚙️ → Uso e faturamento)
   - Necessário para Cloud Functions
   - Em "Orçamentos e alertas" do Google Cloud, crie alerta de **R$ 100/mês**

## 3. Obter a chave do Gemini

1. Acesse https://aistudio.google.com/apikey → **Criar chave de API**
   (pode usar o mesmo projeto Google Cloud do Firebase)
2. Guarde a chave — ela vai para o Secret Manager no passo 4 (NUNCA no código)

## 4. Instalar o Firebase CLI e fazer o deploy do backend

No terminal, na raiz do projeto:

```bash
npm install -g firebase-tools
firebase login
firebase use --add          # selecione o projeto nutrimetric-xxxxx

# Segredos (cole a chave quando o CLI pedir)
firebase functions:secrets:set GEMINI_API_KEY
firebase functions:secrets:set REVENUECAT_WEBHOOK_TOKEN   # qualquer string longa aleatória por enquanto

# Deploy das functions + regras de segurança do Firestore
cd functions && npm install && cd ..
firebase deploy --only functions,firestore:rules
```

## 5. Testar

1. Abra o projeto no Android Studio e rode o app
2. A tela de **login com Google** deve aparecer (se cair direto na home, o
   `google-services.json` não está em `app/`)
3. Faça login → tire uma foto de um prato → a análise deve funcionar
4. Tire uma **segunda** foto no mesmo dia → deve aparecer a mensagem de limite
   (1 foto/dia FREE) — é o paywall da Fase 2 que vai abrir aqui
5. Desinstale e reinstale o app → o limite **continua** valendo (quota no servidor ✅)

---

## Próximos passos (Fase 0 restante — podem ser feitos em paralelo)

| Conta | Onde | Observação |
|---|---|---|
| **Google Play Console** | https://play.google.com/console/signup | US$ 25 único. Conta pessoal nova exige 12 testadores × 14 dias antes de produção — criar CEDO |
| **RevenueCat** | https://app.revenuecat.com/signup | Grátis até US$ 2,5k/mês de receita. Necessário na Fase 2 |
| **Página de privacidade** | GitHub Pages serve | Obrigatória para publicar (LGPD) |

Quando o passo 5 estiver verde, me avise: começo a **Fase 2 (RevenueCat + paywall)**.
