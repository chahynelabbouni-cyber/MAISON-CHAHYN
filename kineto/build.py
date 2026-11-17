"""Assemble site/ en un seul fichier dist/index.html (pratique pour un aperçu hors ligne)."""
from pathlib import Path
root = Path(__file__).parent / "site"
html = (root / "index.html").read_text(encoding="utf-8")
html = html.replace('<link rel="stylesheet" href="style.css">', "<style>\n" + (root / "style.css").read_text(encoding="utf-8") + "</style>")
for js in ("engine.js", "app.js"):
    html = html.replace(f'<script src="{js}" defer></script>', "")
html = html.replace("</body>", "".join(f"<script>\n{(root / js).read_text(encoding='utf-8')}</script>\n" for js in ("engine.js", "app.js")) + "</body>")
out = Path(__file__).parent / "dist" / "index.html"
out.parent.mkdir(exist_ok=True)
out.write_text(html, encoding="utf-8")
print("OK ->", out)
