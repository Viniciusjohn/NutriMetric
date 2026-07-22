# 📋 Gabarito do formulário "Data Safety" (Play Console)

Este documento mapeia cada categoria do formulário de segurança de dados da
Play Console para a resposta correta, com base no que o código do
NutriMetric **realmente coleta hoje** (conferido no repositório, não é
suposição). Você só precisa abrir o formulário em **Play Console → seu app →
Política → Segurança dos dados** e marcar exatamente o que está aqui.

> ⚠️ Sou uma IA revisando o código, não advogado. As classificações abaixo
> seguem minha leitura das categorias oficiais do Google, mas a
> responsabilidade legal pelo formulário é sempre do desenvolvedor
> (você). Se tiver dúvida em algum item, é mais seguro marcar a opção mais
> conservadora (declarar coleta) do que a mais permissiva.

## 1. "O app coleta ou compartilha algum dos tipos de dados de usuário exigidos?"
**Resposta: Sim.**

## 2. Tipos de dados coletados

| Categoria Play Console | Coletado? | Compartilhado com terceiros? | Motivo | Onde no código |
|---|---|---|---|---|
| **Informações pessoais → Nome** | Sim | Não* | Funcionalidade do app, gerenciamento de conta | `AuthRepository` (Google Sign-In) |
| **Informações pessoais → E-mail** | Sim | Não* | Funcionalidade do app, gerenciamento de conta | `AuthRepository` (Google Sign-In) |
| **Informações pessoais → ID de usuário** | Sim | Não | Funcionalidade do app | Firebase Auth `uid`, usado em todo `users/{uid}` no Firestore |
| **Fotos e vídeos → Fotos** | Sim | Não* | Funcionalidade do app (análise nutricional por IA) | `AnalysisRepository.analisarFoto()` → Cloud Function `analyzePhoto` → Gemini |
| **Saúde e fitness → Informações de saúde** | Sim | Não | Funcionalidade do app | Peso (`WeightEntity`), água (`WaterEntity`), calorias/macros (`DailyConsumptionEntity`), perfil do onboarding (altura/idade/sexo/atividade/objetivo em `ProfileRepository`) |
| **Informações financeiras → Histórico de compras** | Sim | Não* | Funcionalidade do app (validar assinatura Premium) | RevenueCat SDK (`SubscriptionRepository`) + webhook `revenuecatWebhook` |
| **Atividade no app → Interações no app** | Sim | Não | Analytics | `AnalyticsRepository` — eventos `onboarding_complete`, `photo_analyzed`, `paywall_view`, `trial_start`, `purchase` |
| **Atividade no app → Outro conteúdo gerado pelo usuário** | Sim | Não | Funcionalidade do app | Mensagens do chat com a Nutri IA (`ChatMessageEntity`, processadas pela Cloud Function `chat` → Gemini) |
| **Identificadores do dispositivo ou outros** | Sim | Não | Funcionalidade do app (notificações push) | Token FCM (`PushRepository`/`registerFcmToken`) |
| **Informações e desempenho do app → Registros de falhas** | Sim | Não* | Analytics, diagnóstico | Firebase Crashlytics (dependência já incluída; ativa quando o plugin do Crashlytics roda, ou seja, quando `google-services.json` existe) |
| **Informações e desempenho do app → Diagnósticos** | Sim | Não* | Analytics, diagnóstico | Firebase Performance/Crashlytics padrão |

*\* Gemini (Google), RevenueCat e Firebase/Crashlytics processam esses dados
como prestadores de serviço contratados para operar o app — não é venda nem
compartilhamento para fins próprios deles. O Google Play trata isso como
"coleta", não "compartilhamento com terceiros", **desde que os termos de
serviço desses provedores confirmem esse papel de processador** — vale
conferir os Termos/DPA do RevenueCat e da API do Gemini ao preencher, já que
esse é o único ponto realmente jurídico desta tabela.*

### Categorias que o app NÃO coleta
Localização, contatos, calendário, arquivos e documentos, áudio, navegação
na web, apps instalados, histórico de busca no app, informações financeiras
além de histórico de compras (não processamos cartão de crédito — isso é
100% do Google Play Billing), raça/etnia, religião, orientação sexual,
número de telefone, endereço.

## 3. Para cada tipo marcado acima

- **Os dados são criptografados em trânsito?** Sim (HTTPS/TLS em todas as
  chamadas — Firebase SDK e Retrofit/OkHttp usam TLS por padrão).
- **Os usuários podem solicitar a exclusão dos dados?** Sim — fluxo in-app em
  **Configurações → Conta → Excluir Conta**, que chama a Cloud Function
  `deleteAccount` (apaga `users/{uid}` recursivamente no Firestore + a conta
  no Firebase Auth, ver `functions/src/index.ts`).
- **A coleta é obrigatória ou opcional?**
  - Nome/e-mail/ID de usuário: obrigatório (login é pré-requisito pra usar o
    app).
  - Fotos: opcional na prática — dá pra registrar refeição manualmente
    (`ManualEntryScreen`) sem tirar foto, mas é obrigatório para a
    funcionalidade central de análise por IA.
  - Dados de saúde (peso, água, calorias, perfil): opcional — o app funciona
    sem preencher tudo, mas as metas calculadas dependem do perfil do
    onboarding.
  - Histórico de compras: obrigatório apenas para quem assina o Premium.
  - Token FCM / notificações: opcional (usuário pode negar a permissão
    `POST_NOTIFICATIONS`; sem ela o app funciona normalmente, só não recebe
    push).
  - Analytics/crash/diagnóstico: geralmente tratado como obrigatório pelo
    Google (não há como o usuário optar por não gerar eventos de uso), mas
    confirme a opção mais adequada disponível no formulário.

## 4. Práticas de segurança de dados (seção separada do formulário)

- ☑ "Os dados são criptografados em trânsito"
- ☑ "Você pode solicitar a exclusão dos dados" (fluxo in-app já existe)
- ☐ "Este app segue a Política de Famílias do Google Play" — **NÃO marque**,
  o app não é direcionado a crianças (dado de saúde + IA conversacional não
  são apropriados para o público infantil; isso também deve refletir na
  classificação etária do app, ver `docs/LAUNCH-CHECKLIST.md`).

## 5. Link da política de privacidade

Cole a URL publicada (ver `docs/LAUNCH-CHECKLIST.md` sobre o GitHub Pages):
```
https://viniciusjohn.github.io/NutriMetric/privacy-policy.html
```
