# Assets da ficha da Play Store

- **feature-graphic.png** (1024×500) — gráfico de destaque obrigatório da
  ficha da loja. Gerado a partir de `feature-graphic.source.html` (renderizado
  com Chromium headless a 1024×500). Para editar: mude o HTML e re-renderize
  com `chrome --headless=new --window-size=1024,500 --screenshot=out.png file://.../feature-graphic.source.html`.
- **Ícone 512×512:** exportar do `app/src/main/res/mipmap-*/ic_launcher` (a
  Play Console exige 512×512 PNG 32-bit).
- **Screenshots:** capturar no device real — ver guia em `../PLAY-STORE-LISTING.md`.
