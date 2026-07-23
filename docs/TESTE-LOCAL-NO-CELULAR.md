# 📱 Testar o app no seu celular (a partir do PC)

Guia rápido pra rodar o NutriMetric no seu Android agora, sem precisar de
Play Console nem nada da Fase 0. Dois caminhos: **rápido** (vê a UI/telas
locais, 0 configuração) ou **completo** (com foto por IA e chat, precisa do
Firebase configurado — ~15 min, ver `SETUP-FIREBASE.md`).

---

## 1. Baixar o código

O trabalho está todo na branch `claude/code-app-analysis-kzhb58` (ainda não
foi mergeada na `main`). No seu PC:

```bash
git clone https://github.com/viniciusjohn/NutriMetric.git
cd NutriMetric
git checkout claude/code-app-analysis-kzhb58
```

Se já tinha clonado antes, só dá um `git pull` na branch.

## 2. Instalar o Android Studio (se ainda não tem)

Baixe em **https://developer.android.com/studio** e instale (Windows/Mac/
Linux). No primeiro setup, deixe ele baixar o **Android SDK** (assistente
automático). Não precisa mexer em nada avançado.

> ⚠️ Este projeto usa **AGP 9.1.1**, que exige **Gradle 9.3.1+**. O repo não
> tem o `gradlew` versionado ainda, então o Android Studio vai te oferecer
> pra criar o wrapper automaticamente ao abrir o projeto — aceite. Se pedir
> pra baixar uma versão nova do Gradle, deixe baixar (é rápido, roda uma vez só).

## 3. Abrir o projeto

**Android Studio → Open** → selecione a pasta `NutriMetric` (a raiz, onde
tem o `settings.gradle.kts`). Espere o **Gradle Sync** terminar (barra de
progresso embaixo) — pode levar alguns minutos na primeira vez.

## 4. Preparar o celular

1. **Ativar Opções do desenvolvedor:** Configurações → Sobre o telefone →
   toque 7x em "Número da versão" (ou "Build number").
2. **Ativar Depuração USB:** Configurações → Sistema → Opções do
   desenvolvedor → ligar "Depuração USB".
3. **Conectar o cabo USB** no PC. Vai aparecer um popup no celular
   "Permitir depuração USB?" → **Permitir** (marque "sempre permitir deste
   computador" se quiser).

> Alternativa sem cabo: Opções do desenvolvedor → "Depuração sem fio" →
> parear pelo Wi-Fi (mesmo processo, sem cabo).

## 5. Rodar no celular

No Android Studio, com o celular conectado:
1. No topo, o seletor de dispositivo deve mostrar o nome do seu aparelho
   (se não aparecer, confira o passo 4 e o cabo).
2. Selecione o módulo **app** e clique no botão **▶ Run** (verde).
3. Primeira vez demora um pouco (compilação completa). O app abre sozinho
   no celular quando terminar.

**Via linha de comando** (se preferir, depois que o Android Studio gerou o
`gradlew` no passo 2):
```bash
./gradlew installDebug   # Linux/Mac
gradlew.bat installDebug # Windows
```

---

## 6. O que funciona sem configurar o Firebase (caminho rápido)

Sem `google-services.json`, o app abre em **"modo dev"**: pula o login e
vai direto pra Home. Dá pra testar:
- ✅ Onboarding completo (perguntas da Nutri, cálculo de metas)
- ✅ Registro manual de refeição (busca na Tabela TACO local)
- ✅ Água, peso, streak, histórico, relatório semanal
- ✅ Lembretes/notificações locais
- ✅ Navegação e toda a UI

**Não funciona** sem o backend (mostra erro ao tentar usar, mas não crasha —
isso já foi corrigido nesta sessão):
- ❌ Análise de foto por IA (precisa da Cloud Function `analyzePhoto`)
- ❌ Chat com a Nutri (precisa da Cloud Function `chat`)
- ❌ Compra de assinatura Premium de verdade (precisa do RevenueCat)
- ❌ Push de reengajamento (a notificação local de lembrete continua funcionando)

## 7. Caminho completo (com IA de verdade)

Pra testar foto/chat funcionando, siga `docs/SETUP-FIREBASE.md` (criar
projeto Firebase, baixar `google-services.json` pra `app/`, deploy das
Cloud Functions). Depois disso, volte no passo 5 e rode de novo — o app
detecta o `google-services.json` sozinho e habilita o login + as
funcionalidades de IA.

---

## Problemas comuns

- **"Gradle sync failed" / versão do Gradle:** deixe o Android Studio criar
  o wrapper (ele pergunta na primeira abertura). Se recusar, vá em
  **File → Settings → Build Tools → Gradle** e configure "Gradle JDK" pra
  uma versão 17+.
- **Celular não aparece na lista:** confira se a Depuração USB está
  realmente ligada e se você apertou "Permitir" no popup do celular. Tente
  trocar o cabo (alguns cabos só carregam, não transferem dados).
- **App abre mas trava numa tela em branco:** rode com o **Logcat** aberto
  (janela embaixo no Android Studio, aba "Logcat") pra ver o erro exato.
