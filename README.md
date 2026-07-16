# NutriMetric 🥗

Nutricionista de bolso: analise fotos de refeições brasileiras com IA, escaneie
códigos de barras e acompanhe suas metas de macronutrientes (base Tabela TACO).

- **App Android** em Kotlin + Jetpack Compose (`app/`)
- **Backend** em Cloud Functions for Firebase (`functions/`) — as chaves de IA
  vivem apenas no servidor, e a quota diária de fotos é validada no Firestore
- **Roadmap do produto**: ver [ROADMAP.md](ROADMAP.md)

## Rodar localmente

**Pré-requisitos:** [Android Studio](https://developer.android.com/studio)

1. Abra o Android Studio → **Open** → selecione a pasta do projeto
2. Siga o guia [docs/SETUP-FIREBASE.md](docs/SETUP-FIREBASE.md) para criar o
   projeto Firebase e baixar o `google-services.json` para `app/`
   - Sem esse arquivo o app compila e roda em **modo dev** (sem login e sem
     análise por IA — a análise depende do backend)
3. Rode o app em um emulador ou dispositivo físico com câmera

## Backend (Cloud Functions)

```bash
cd functions
npm install
npm run build          # compila o TypeScript
firebase deploy --only functions,firestore:rules
```

Segredos necessários (nunca commitados):

```bash
firebase functions:secrets:set GEMINI_API_KEY
firebase functions:secrets:set REVENUECAT_WEBHOOK_TOKEN
```

## Testes

```bash
./gradlew test                    # unit tests (Robolectric/Roborazzi)
./gradlew connectedAndroidTest    # instrumentados (dispositivo/emulador)
```
