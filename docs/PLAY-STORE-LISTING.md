# 🏷️ Ficha da Play Store — copy PT-BR (pronta pra colar)

Textos prontos para **Play Console → Presença na loja → Página principal da
loja**. Limites de caracteres do Google entre parênteses. Ajuste o que quiser,
mas os limites são rígidos — não ultrapasse.

---

## Nome do app (máx. 30 caracteres)
```
NutriMetric: Nutrição com IA
```
(28 caracteres. Alternativa mais curta: `NutriMetric` — 11 caracteres.)

## Descrição curta (máx. 80 caracteres)
```
Fotografe seu prato e a IA calcula calorias e macros. Sua nutri de bolso.
```
(73 caracteres.)

## Descrição longa (máx. 4000 caracteres)
```
O NutriMetric é a forma mais rápida de acompanhar sua alimentação: tire uma
foto do prato e a inteligência artificial identifica os alimentos e estima
calorias, proteínas, carboidratos e gorduras — com base na Tabela TACO
brasileira.

📸 ANÁLISE POR FOTO
Aponte a câmera para a refeição e pronto. Nada de procurar alimento por
alimento numa lista gigante. A IA reconhece pratos brasileiros de verdade —
do PF ao açaí.

🎯 METAS PERSONALIZADAS
Um onboarding rápido calcula suas metas diárias de calorias e macros com base
no seu peso, altura, idade e objetivo (fórmula Mifflin-St Jeor). Sem chute.

💬 NUTRI IA (Premium)
Converse com a Nutri, sua assistente de nutrição. Ela comenta suas refeições,
responde dúvidas e te ajuda a chegar nas metas — sempre no jeitinho brasileiro.

📊 ACOMPANHE SUA EVOLUÇÃO
- Registro de água e peso
- Sequência de dias (streak) pra manter o hábito
- Histórico diário e relatório semanal com gráficos
- Lembretes pra não esquecer de registrar

🥗 BASE BRASILEIRA
Todos os cálculos usam a Tabela TACO (Tabela Brasileira de Composição de
Alimentos), então os números fazem sentido pra comida de verdade que você come.

────────────────────
PLANOS
• GRÁTIS: 1 análise por foto por dia + todo o acompanhamento (água, peso,
  metas, histórico).
• PREMIUM: 15 análises por dia + chat ilimitado com a Nutri IA + relatório
  semanal com análise escrita. Assinatura mensal ou anual, com período de
  teste grátis.

────────────────────
IMPORTANTE
O NutriMetric é uma ferramenta de acompanhamento e NÃO substitui um
nutricionista ou médico. As estimativas geradas por IA são aproximações e não
devem ser a única base para decisões de saúde. Consulte um profissional para
orientação individualizada.
```

## Palavras-chave / termos de destaque
Não há campo dedicado de keywords na Play Store (o algoritmo indexa nome +
descrições), mas garanta que estes termos apareçam naturalmente nos textos
acima (já aparecem): *contador de calorias, nutrição, dieta, macros, TACO,
IA, foto, emagrecer, saúde, refeição, proteína*.

---

## Categoria e classificação
- **Categoria:** Saúde e fitness
- **Tags:** nutrição, contador de calorias, dieta
- **Classificação etária:** responda o questionário de conteúdo declarando
  que **não** é direcionado a crianças (app de saúde + IA conversacional).
  Deve resultar em "Livre" / "Classificação Indicativa L", mas quem define é
  o questionário do IARC — responda com sinceridade.

---

## Recursos gráficos obrigatórios (você gera, ver guia de screenshots abaixo)
| Recurso | Especificação Google | Status |
|---|---|---|
| Ícone do app | 512×512 px, PNG 32-bit | ✅ já existe no projeto (`ic_launcher`) — exporte em 512px |
| Gráfico de destaque (feature graphic) | 1024×500 px, PNG/JPG | ⬜ criar (pode ser simples: logo + tagline sobre fundo verde) |
| Screenshots de celular | mín. 2, máx. 8; 16:9 ou 9:16; lado 320–3840 px | ⬜ capturar no device (guia abaixo) |

---

## 📸 Guia de screenshots (capturar no device real)

**Decisão do projeto:** os screenshots saem do app rodando de verdade no seu
aparelho durante o teste fechado (que é obrigatório de qualquer forma), não de
render automático — fidelidade perfeita e zero risco.

**Como capturar:** com o app instalado (build de teste), abra cada tela abaixo
e use o botão de captura do próprio Android (Power + Volume↓). Salve os PNGs.

**Ordem sugerida (6 telas, conta a história do produto):**
1. **Home com refeições registradas** — mostra o dashboard de progresso
   (calorias/macros do dia), cards de água/peso e o badge de streak 🔥. É a
   "cara" do app.
2. **Análise de foto** — a tela `AnalysisScreen`/`PlateReviewScreen` logo após
   fotografar um prato, com os alimentos detectados e os valores. É o
   diferencial nº 1 — deixe em destaque.
3. **Chat com a Nutri** — uma conversa curta mostrando a Nutri comentando uma
   refeição (vende o Premium).
4. **Relatório semanal** — a `WeeklyReportScreen` com o gráfico de calorias dos
   7 dias e as médias de macros.
5. **Onboarding / metas calculadas** — a tela de resumo do onboarding com as
   metas (mostra que é personalizado).
6. **Paywall** — a `PaywallScreen` com os planos (opcional, mas ajuda quem
   está decidindo).

**Dicas:**
- Use dados realistas antes de capturar (registre 2–3 refeições, alguns dias de
  histórico) pra não aparecer tela vazia.
- Prefira o tema claro pra screenshots (mais legível na loja), a não ser que o
  app fique melhor no escuro.
- Molduras de celular são **opcionais** na Play Store — screenshot cru é
  aceito. Se quiser moldura bonita, ferramentas como o "Device Art Generator"
  do Android ou sites de mockup resolvem, mas não é obrigatório.
- Capriche nas 2 primeiras (Home e Análise por Foto): são as que aparecem em
  destaque na busca.
