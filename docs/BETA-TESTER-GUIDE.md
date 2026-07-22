# 🧪 Kit do teste fechado — NutriMetric

Material pronto para o **teste fechado da Play Store** (12 testadores × 14 dias,
requisito de conta nova antes de publicar em produção). Use as seções abaixo:
o **convite** você manda pros testadores; o **roteiro** e o **como reportar**
você compartilha com eles; o **smoke test** é seu checklist antes de convidar
gente.

---

## 1. Convite (copie e mande pros 12 testadores)

> **Oi! Tô lançando um app e preciso da sua ajuda 🙏**
>
> O **NutriMetric** é um app de nutrição: você tira uma foto do prato e a IA
> calcula as calorias e os macros (proteína, carbo, gordura). Preciso de
> testadores por **14 dias** pra poder publicar na Play Store.
>
> **É rapidinho:**
> 1. Toque neste link no celular Android: **[COLE AQUI O LINK DE TESTE DA PLAY]**
> 2. Toque em "Tornar-se testador" e depois em "Baixar no Google Play".
> 3. Instale, faça login com o Google e use normalmente alguns dias.
>
> Se topar, me avisa que você entrou 🙌 Qualquer bug ou coisa estranha, me
> manda print. Valeu demais!

> ⚠️ **Você precisa coletar o e-mail Google de cada testador** e adicioná-los na
> Play Console (Teste fechado → Testadores) — só quem está na lista consegue
> instalar. O link de teste só funciona pra esses e-mails.

---

## 2. Roteiro de teste (mande pros testadores — "faça esse caminho")

> **O que testar (leva ~10 min, e depois é só usar no dia a dia):**
>
> 1. **Primeiro acesso:** faça login com o Google. Responda as perguntas da
>    Nutri (peso, altura, idade, objetivo). Confira se as metas calculadas
>    aparecem no fim.
> 2. **Foto de refeição:** na tela inicial, toque na câmera, fotografe um prato
>    (ou escolha da galeria) e veja se a IA lista os alimentos com calorias.
>    Confirme/salve a refeição.
> 3. **Nutri comenta:** depois de salvar, veja se a Nutri comenta a refeição.
> 4. **Registros:** registre água e seu peso na tela inicial.
> 5. **Chat (Premium):** abra o chat (ícone no topo) e tente mandar uma
>    pergunta. No plano grátis vai aparecer a tela de assinatura — tudo bem,
>    **não precisa pagar**. Se quiser testar a compra, use o modo de teste
>    (você é testador, não será cobrado de verdade).
> 6. **Relatório semanal:** abra o ícone de relatório e veja o gráfico.
> 7. **Volte no dia seguinte:** registre de novo pra ver a sequência (🔥 streak)
>    aumentar.
>
> **Se algo travar, ficar estranho ou não fizer sentido, me avisa (ver abaixo).**

---

## 3. Como reportar um bug (mande pros testadores)

> **Achou um problema? Me manda uma mensagem com:**
> - **O que você fez** (ex: "tirei foto de um prato de feijoada");
> - **O que aconteceu** (ex: "o app fechou sozinho" / "não apareceu nada");
> - **O que você esperava** que acontecesse;
> - **Print da tela** (segura Power + Volume pra baixo);
> - **Seu celular e versão do Android** (Configurações → Sobre o telefone).
>
> Quanto mais detalhe, mais rápido eu conserto. Obrigado! 🙏

---

## 4. Smoke test (SEU checklist antes de convidar os 12)

Rode você mesmo, num device real, antes de abrir o teste — pega os erros
grosseiros antes de gastar o convite dos testadores:

- [ ] Instala e abre sem crash.
- [ ] Login com Google funciona; segundo login não repete o onboarding.
- [ ] Onboarding calcula metas e elas refletem na tela inicial.
- [ ] Foto → análise → alimentos listados → salvar → aparece no dia.
- [ ] FREE trava na 2ª foto do dia com a tela de assinatura (paywall).
- [ ] Nutri comenta a refeição após salvar.
- [ ] Chat: FREE vê upsell; (se testar compra sandbox) Premium destrava o chat.
- [ ] Água e peso salvam e persistem ao reabrir o app.
- [ ] Relatório semanal renderiza o gráfico.
- [ ] Notificação de lembrete dispara no horário configurado.
- [ ] **Excluir conta** (Configurações → Conta) apaga tudo e desloga.
- [ ] Reabrir depois de excluir a conta leva de volta ao login.

> Dica: é durante este smoke test que você tira os **screenshots** da ficha da
> loja (ver `PLAY-STORE-LISTING.md`, seção de screenshots).

---

## Referências
- Passo a passo de publicação: `LAUNCH-CHECKLIST.md` (Parte F).
- Configuração de assinatura/sandbox: `SETUP-REVENUECAT.md`.
