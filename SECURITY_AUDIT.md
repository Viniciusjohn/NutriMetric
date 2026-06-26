# Auditoria de Segurança — NutriMetric

Data: 2026-06-26

## Escopo

Varredura do repositório em `c:\NutriMetric` em busca de:

- Chaves/tokens hardcoded (padrões comuns: AIza…, sk-…, gsk_…, ghp_…, AWS AKIA/ASIA, Slack xox…)
- Senhas/tokens hardcoded (`password=`, `secret=`, `token=`, `Authorization: Bearer …`)
- Endpoints sensíveis (IPs privados/localhost)
- Dados pessoais (CPF/email/telefone)
- Arquivos de assinatura Android (`.jks`, `.keystore`, etc.)
- `google-services.json`
- Presença e cobertura de `.gitignore`
- Arquivos grandes (>10MB)
- Histórico Git (quando disponível)

## Findings

### F-001 — API key hardcoded (placeholder) em arquivos JS

- **Severidade:** MÉDIO
- **Por que importa:** mesmo sendo placeholder, o padrão incentiva substituir por chave real e acidentalmente versionar (especialmente em front-end/JS). Além disso, a chave vai para URL (`?key=`), o que aumenta risco de vazamento por logs/proxies.
- **Ocorrências:**
  - [ai-service.js:L3](file:///c:/NutriMetric/ai-service.js#L3)
    - Trecho (mascarado): `const API_KEY = "SUA_...AQUI";`
  - [ai-service.js:L4](file:///c:/NutriMetric/ai-service.js#L4)
    - Trecho (mascarado): `...generateContent?key=${API_KEY}`
  - [ai-service.js:L3](file:///c:/NutriMetric/src/services/ai-service.js#L3)
    - Trecho (mascarado): `const API_KEY = "SUA_...AQUI";`
  - [ai-service.js:L5](file:///c:/NutriMetric/src/services/ai-service.js#L5)
    - Trecho (mascarado): `...generateContent?key=${API_KEY}`
- **Correção sugerida:**
  - Remover qualquer chave (mesmo placeholder) de arquivo versionado e ler via env/secret manager.
  - No Android, manter o caminho atual via Secrets Gradle Plugin + `.env`/`.env.example`.
  - Se este JS for usado em runtime: migrar para backend (não rodar em cliente) e injetar chave via variável de ambiente no servidor.

### F-002 — Senha de debug keystore hardcoded (padrão AOSP)

- **Severidade:** BAIXO
- **Por que importa:** não é secret real (senha padrão do debug keystore), mas é um padrão de “credencial hardcoded” que pode virar problema se for copiado para release por engano.
- **Ocorrências:**
  - [app/build.gradle.kts:L34-L37](file:///c:/NutriMetric/app/build.gradle.kts#L34-L37)
    - Trecho (mascarado): `storePassword = "an...id"` / `keyPassword = "an...id"`
- **Correção sugerida:**
  - Garantir que essa signingConfig seja usada apenas em `debug`.
  - Garantir que `release` dependa exclusivamente de variáveis de ambiente (`STORE_PASSWORD`, `KEY_PASSWORD`, etc.) e de um keystore ignorado pelo Git.

## Verificações Complementares

### .gitignore

- **Existe:** sim — [.gitignore](file:///c:/NutriMetric/.gitignore)
- **Cobertura atual (parcial):**
  - cobre `.gradle`, `.idea` e `/build`
  - cobre `/local.properties` e `.env`
  - não cobre explicitamente vários itens desejados (ex.: `*.jks`, `*.keystore`, `google-services.json`, `/models/`, `/datasets/`, logs, etc.)
- **Recomendação:** executar o PROMPT 0.2 para mesclar um `.gitignore` completo sem duplicações.

### google-services.json / keystores / local.properties / .env real

- `google-services.json`: não encontrado
- `*.jks` / `*.keystore`: não encontrados
- `local.properties`: não encontrado
- `.env` real: não encontrado (apenas `.env.example` existe, como deveria)

### Endpoints internos/privados

- Nenhum match de `localhost`, `127.0.0.1` ou IPs privados foi encontrado no código escaneado.
- Endpoints públicos identificados (esperados):
  - Gemini: `generativelanguage.googleapis.com`
  - NVIDIA: `integrate.api.nvidia.com`
  - OpenFoodFacts: `world.openfoodfacts.org`

### Dados pessoais (CPF/telefone BR)

- Nenhum match encontrado para CPF/telefone BR nos padrões escaneados.

### Histórico Git (secrets em commits)

- **Não aplicável neste ambiente:** não há pasta `.git` no snapshot atual (`GIT_REPO=NO`), então não é possível auditar `git log --all -p`.
- **Ação recomendada:** ao rodar localmente em um clone com Git, executar varredura do histórico por `api_key|password|secret|token` antes de publicar o repositório.

### Arquivos grandes (>10MB)

- Nenhum arquivo acima de 10MB foi identificado neste snapshot.
- Observações:
  - `app/src/main/assets/model.tflite` está em tamanho muito pequeno (placeholder).
  - `app/src/main/assets/database/taco.db` também está pequeno (provável base reduzida/placeholder).

## Recomendações Prioritárias

1. Executar PROMPT 0.2 para endurecer `.gitignore` (principal mitigação de vazamento acidental).
2. Executar PROMPT 0.3 para padronizar `README_SETUP.md` e `local.properties.example`.
3. Se os arquivos JS (`ai-service.js`) forem usados em runtime, migrar para backend/Cloud Function e remover qualquer referência a “API_KEY” hardcoded em código versionado.

