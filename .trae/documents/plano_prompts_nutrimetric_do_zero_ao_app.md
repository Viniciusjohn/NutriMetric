## Resumo

Este plano transforma a sequência de 11 prompts (FASE 0 → FASE 4) em um roteiro executável no TRAE, com checkpoints de validação entre tarefas e com ajustes baseados no estado real do repositório em `c:\NutriMetric`.

Objetivo final: app Android (Compose + Room + DataStore) funcional e resiliente, com segurança de secrets, persistência de imagens sem Base64 no Room, pipeline TFLite local com fallback para API, histórico por data e metas nutricionais configuráveis, além de release + testes.

## Análise do Estado Atual (baseado na inspeção do repo)

- Projeto Android existe em [app](file:///c:/NutriMetric/app), com Kotlin/Compose e Room em [app/build.gradle.kts](file:///c:/NutriMetric/app/build.gradle.kts).
- Já existe `.gitignore` na raiz em [.gitignore](file:///c:/NutriMetric/.gitignore), porém está incompleto vs. a lista desejada (faltam, por exemplo, padrões para keystore, google-services.json, datasets, modelos e vários artefatos comuns).
- Já existe `.env.example` em [.env.example](file:///c:/NutriMetric/.env.example).
- O app já usa Secrets Gradle Plugin configurado para `.env` e `.env.example` em [app/build.gradle.kts](file:///c:/NutriMetric/app/build.gradle.kts#L63-L68).
- Já existe `DatabaseProvider` em [DatabaseProvider.kt](file:///c:/NutriMetric/app/src/main/java/com/example/data/local/DatabaseProvider.kt), mas a implementação difere do prompt (não tem `clearInstance()`, e há lógica de “patch” do `room_master_table` para o `taco.db`).
- Já existe pipeline TFLite com fallback em [TfliteFoodClassifier.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ml/TfliteFoodClassifier.kt) e o `MainViewModel` tenta TFLite primeiro em [MainViewModel.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt#L288-L337).
- Já existem DataStore + UI de metas em [GoalsRepository.kt](file:///c:/NutriMetric/app/src/main/java/com/example/repository/GoalsRepository.kt) e [SettingsScreen.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ui/screens/SettingsScreen.kt) (arquivo presente no repo, não inspecionado neste plano).
- Persistência de imagem ainda não está 100% conforme a regra “NUNCA Base64 no banco”:
  - [MealEntity.kt](file:///c:/NutriMetric/app/src/main/java/com/example/data/local/MealEntity.kt) ainda contém `imageBase64`.
  - [MealRepository.kt](file:///c:/NutriMetric/app/src/main/java/com/example/repository/MealRepository.kt) grava `imageBase64` como string vazia, mas mantém a coluna; e `deleteMeal()` não apaga arquivo do disco.
  - [MainViewModel.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt) ainda carrega Base64 para transporte no estado de UI (isso é ok), mas não pode persistir no Room.
- Varredura inicial por padrões óbvios de chaves (AIza…, sk-…, etc.) não encontrou matches no conteúdo do repo; achados leves incluem passwords de debug keystore em [app/build.gradle.kts](file:///c:/NutriMetric/app/build.gradle.kts#L32-L37).

## Decisões e Regras (para execução dos prompts)

- Cada prompt será tratado como tarefa isolada, com:
  - escopo rígido,
  - lista de arquivos-alvo,
  - critérios de aceite,
  - validação (build/test ou verificação de artefatos).
- Quando o repo já tiver parte/totalidade do deliverable, o prompt será executado como “auditar + ajustar o mínimo” para cumprir as regras do prompt sem reescrever desnecessariamente.
- Segurança: nenhum secret real será adicionado ao repositório. Apenas placeholders e exemplos.

## Plano de Execução (11 prompts)

### PROMPT 0.1 — Auditoria de Segurança (primeiro)

**Objetivo:** gerar um relatório Markdown (ex.: `SECURITY_AUDIT.md` na raiz) sem modificar arquivos de código.

**Como executar (passos concretos):**
- Varredura por padrões de chaves/tokens em todo o repo (Kotlin/Gradle/JSON/JS/Python/Markdown).
- Varredura por credenciais hardcoded (`password=`, `secret=`, `token=`, `Authorization: Bearer ...`).
- Varredura por dados pessoais (CPF/email/telefone) em strings e docs.
- Busca por `google-services.json`, `*.jks`, `*.keystore`, `.env`, `local.properties`.
- Inspeção do `.gitignore` existente e comparação com a lista alvo.
- Verificação de histórico Git:
  - executar um comando equivalente a `git log --all -p` e filtrar por termos críticos (api_key|password|secret).
- Verificação de arquivos grandes (>10MB):
  - listar recursivamente e ordenar por tamanho para identificar suspeitos.

**Achados já observados (prévia, sujeito à auditoria completa):**
- `storePassword = "android"` / `keyPassword = "android"` em [app/build.gradle.kts](file:///c:/NutriMetric/app/build.gradle.kts#L32-L37) (BAIXO; debug keystore padrão).
- Endpoints públicos usados: Gemini, NVIDIA, OpenFoodFacts em [RetrofitClient.kt](file:///c:/NutriMetric/app/src/main/java/com/example/data/remote/RetrofitClient.kt) e `ai-service.js` (BAIXO/MÉDIO; não é secret, mas confirma superfícies sensíveis).

**Critérios de aceite:**
- Relatório Markdown com:
  - arquivo + linha por finding,
  - trechos mascarados,
  - severidade,
  - correção sugerida,
  - checklist `.gitignore`, histórico Git e arquivos grandes.

### PROMPT 0.2 — Criar/Mesclar .gitignore Completo

**Arquivos-alvo:**
- [.gitignore](file:///c:/NutriMetric/.gitignore) (raiz)
- [app/.gitignore](file:///c:/NutriMetric/app/.gitignore) (manter como mínimo, mas garantir consistência)

**Mudanças propostas:**
- Mesclar (sem duplicar) as entradas solicitadas:
  - Android/Gradle, keystores, `.env*`, `google-services.json`, datasets, modelos, logs, OS.
- Validar que:
  - `.env` e `local.properties` continuam ignorados,
  - artefatos de assinatura (`*.jks`, `*.keystore`) ficam ignorados,
  - exceções para imagens em `res/` permanecem possíveis.

**Critério de aceite:**
- `.gitignore` final cobre integralmente a lista obrigatória do prompt e não perde os ignores já existentes.

### PROMPT 0.3 — Estrutura Segura de Secrets

**Estado atual:** `.env.example` existe e o Secrets Gradle Plugin já está configurado para `.env`/`.env.example` em [app/build.gradle.kts](file:///c:/NutriMetric/app/build.gradle.kts#L63-L68).

**Mudanças propostas (para cumprir o prompt):**
- Criar `local.properties.example` na raiz (não existe atualmente) com `sdk.dir=...` e placeholders.
- Criar `README_SETUP.md` na raiz com instruções:
  - copiar `.env.example → .env`,
  - copiar `local.properties.example → local.properties`,
  - onde inserir as chaves,
  - aviso explícito para nunca commitar `.env`/`local.properties`.
- Ajustar README principal se necessário para referenciar o novo `README_SETUP.md`.

**Decisão:** manter Secrets Gradle Plugin (já existente) como mecanismo primário; não introduzir um segundo mecanismo concorrente (ex.: ler keys do `local.properties`) a menos que seja necessário para compatibilidade local.

**Critério de aceite:**
- Existem apenas arquivos `.example`; nenhum secret real no repo; documentação suficiente para setup local.

### PROMPT 1.1 — Persistência de Imagens (Base64 → Path)

**Problema real no repo:** [MealEntity.kt](file:///c:/NutriMetric/app/src/main/java/com/example/data/local/MealEntity.kt) ainda possui `imageBase64`, e [MealRepository.kt](file:///c:/NutriMetric/app/src/main/java/com/example/repository/MealRepository.kt) não deleta arquivo ao remover meal.

**Arquivos-alvo (mínimo necessário):**
- `app/src/main/java/com/example/data/local/MealEntity.kt`
- `app/src/main/java/com/example/data/local/AppDatabase.kt` (para bump de versão + migration, se o projeto não estiver em destructive)
- `app/src/main/java/com/example/data/local/MealDao.kt` (apenas se necessário por schema, sem alterar filtros por data)
- `app/src/main/java/com/example/repository/MealRepository.kt`
- `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt` (para parar de trafegar Base64 para persistência; manter Base64 apenas em memória/estado, se necessário)
- `app/src/main/java/com/example/ui/screens/HomeScreen.kt` e `PlateReviewScreen.kt` (garantir render via `imagePath`)
- Novo util: `app/src/main/java/com/example/utils/ImageStorageManager.kt`

**Mudanças propostas:**
- Remover coluna `imageBase64` do Room e padronizar `imagePath` como caminho/URI de arquivo persistido no `filesDir/meals/`.
- Implementar `ImageStorageManager.saveBitmap()`:
  - gerar nome único,
  - JPEG 85%,
  - salvar em `context.filesDir/meals/`,
  - retornar caminho/URI consistente,
  - métodos `deleteImage()`, `getBitmap()`, `cleanupOrphanImages()`.
- Refatorar `MealRepository.saveMeal()`:
  - aceitar `Bitmap` ou aceitar `Uri` e converter para Bitmap/bytes internamente,
  - salvar arquivo físico via `ImageStorageManager`,
  - persistir apenas `imagePath`.
- Refatorar `MealRepository.deleteMeal()`:
  - buscar `MealEntity` primeiro,
  - tentar deletar imagem do disco,
  - depois deletar no banco,
  - se arquivo não existir: log warning e seguir.
- Migração Room:
  - adicionar migration para remover a coluna (ou, se o projeto depender de destructive migration, limitar isso ao `debug` e manter migration formal para `release`).

**Critérios de aceite:**
- Room não possui nenhuma coluna Base64 para imagem.
- Inserir 10+ refeições com foto não causa `SQLiteBlobTooBigException`.
- Deletar refeição remove imagem do disco (quando existir).

### PROMPT 1.2 — Singleton DatabaseProvider

**Estado atual:** [DatabaseProvider.kt](file:///c:/NutriMetric/app/src/main/java/com/example/data/local/DatabaseProvider.kt) já existe.

**Mudanças propostas:**
- Alinhar com as regras do prompt:
  - `@Volatile` + `synchronized` (já existe),
  - `applicationContext` (já usado),
  - adicionar `clearInstance()` para testes,
  - reduzir instânciações diretas de Room no projeto (buscar `Room.databaseBuilder` e centralizar).
- Decisão importante: manter ou remover a lógica de patch do `room_master_table` do `taco.db`.
  - A execução deve primeiro rodar build e testes para confirmar se esse patch é realmente necessário para este asset específico.
  - Se não for necessário, simplificar para `createFromAsset("database/taco.db")` conforme prompt.

**Critérios de aceite:**
- Não há mais múltiplos `Room.databaseBuilder` espalhados.
- App inicia consistentemente sem erros de schema do `taco.db`.

### PROMPT 1.3 — Corrigir Erros de Build

**Mudança no prompt original:** não será adicionado comentário `// FIX:` (o projeto tem regra de não adicionar comentários).

**Execução:**
- Rodar `./gradlew assembleDebug` e corrigir erros.
- Rodar testes locais relevantes (`test` + `connectedAndroidTest` se houver).

**Critério de aceite:**
- `assembleDebug` e `test` sem falhas.

### PROMPT 2.1 — Pipeline TFLite On-Device

**Estado atual:** já existem `assets/model.tflite`, `assets/food_labels.txt` e [TfliteFoodClassifier.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ml/TfliteFoodClassifier.kt), com fallback e threshold no [MainViewModel.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt#L303-L337).

**Mudanças propostas para alinhar ao prompt:**
- Adicionar retorno top-3 (label/confidence + lista top3) no classificador.
- Garantir inferência em `Dispatchers.Default` (já está).
- Validar que, quando `model.tflite` é placeholder, o app cai para API sem crash (já há fallback).
- Script de treino Python:
  - criar `scripts/train_tflite.py` e pipeline mínimo (mesmo com dataset sintético).
  - garantir que `model.tflite` gerado seja colocado em `app/src/main/assets/`.

**Critério de aceite:**
- App funciona offline com classificador local (mesmo que seja modelo dummy), e online com fallback.

### PROMPT 3.1 — Calendário / Histórico

**Estado atual:** o Home já tem seletor de data (strip semanal + date picker) e existe [HistoryScreen.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ui/screens/HistoryScreen.kt) (arquivo presente no repo, ainda não inspecionado aqui).

**Mudanças propostas:**
- Decidir entre:
  - manter a UX atual (seletor semanal) e evoluir para “mês completo” em uma tela dedicada, ou
  - adaptar o `HistoryScreen` existente para ser o `CalendarScreen` do prompt.
- Implementar indicadores por dia (verde/amarelo/vermelho) usando os totais já expostos por `dailyConsumptionDao.getAllDailyTotalsGroupedByDate()` em [MainViewModel.kt](file:///c:/NutriMetric/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt#L227-L233).

**Critério de aceite:**
- Selecionar dia passado reflete refeições e totais daquele dia sem mudar schema.

### PROMPT 3.2 — Metas Nutricionais

**Estado atual:** já existe DataStore + UI de settings + metas consumidas na Home via `nutritionGoals`.

**Mudanças propostas:**
- Ajustar nomes e ranges para bater 100% com o prompt (calorias 1200–4000 passo 50, etc.), caso a UI atual difira.
- Garantir preview “Perfil: ...” e botões “Salvar” / “Restaurar padrão”.

**Critério de aceite:**
- Metas persistem entre sessões e a Home reflete corretamente.

### PROMPT 4.1 — Testes + Release + CI

**Mudanças propostas:**
- Adicionar testes unitários e instrumentados conforme lista (ajustando nomes às classes reais do projeto).
- Revisar [proguard-rules.pro](file:///c:/NutriMetric/app/proguard-rules.pro) e `release` em [app/build.gradle.kts](file:///c:/NutriMetric/app/build.gradle.kts#L40-L49) para habilitar `minifyEnabled`/`shrinkResources` quando pronto.
- Criar workflow do GitHub Actions em `.github/workflows/android.yml`.
- Atualizar [README.md](file:///c:/NutriMetric/README.md) com instruções e screenshots (se disponíveis no repo).

**Critério de aceite:**
- CI compila e executa testes em PR/push; release build funciona.

### PROMPT 4.2 — Build Final e Release

**Execução:**
- Rodar `./gradlew assembleRelease`.
- Verificar tamanho do APK e ausência de secrets via inspeção de strings (sem expor dados sensíveis no log).
- Testar offline/online, 10 refeições seguidas e navegação de histórico.
- Tag `v1.0.0-mvp` (apenas se o usuário pedir commit/tag).

**Critério de aceite:**
- Checklist final 100% verde.

## Verificação (padrão entre prompts)

- Sempre que um prompt tocar build/compilação:
  - `./gradlew assembleDebug`
  - `./gradlew test`
- Sempre que tocar persistência/imagem:
  - fluxo “capturar → analisar → salvar 10 refeições → navegar datas → deletar algumas → reiniciar app”.
- Sempre que tocar segurança:
  - reexecutar varreduras de secrets e validar `.gitignore`.

