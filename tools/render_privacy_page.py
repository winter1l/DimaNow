"""Renders PRIVACY.md to the hosted privacy page (D-099), so the Markdown file stays the single source.

Supports the small Markdown subset PRIVACY.md uses: #/## headings, paragraphs, "- " lists, pipe tables,
**bold**, `code` and <https://...> or [text](https://...) links. Everything else is escaped as text.

Usage: python3 tools/render_privacy_page.py PRIVACY.md site/privacy.html
"""
import html
import re
import sys

PAGE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>DIMA Now 개인정보 처리방침</title>
<style>
:root {{ color-scheme: light dark; --bg: #fbf8f9; --fg: #211a1d; --muted: #5c5357; --line: #e4dadf; --accent: #b31169; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #191114; --fg: #efdfe5; --muted: #c9b9c0; --line: #3d3036; --accent: #ffb1c8; }} }}
body {{ margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.65 system-ui, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif; }}
main {{ max-width: 760px; margin: 0 auto; padding: 24px 16px 64px; }}
h1 {{ font-size: 1.6rem; line-height: 1.3; }} h2 {{ font-size: 1.15rem; margin-top: 2rem; }}
a {{ color: var(--accent); }} code {{ font-size: .9em; }}
.table {{ overflow-x: auto; }} table {{ border-collapse: collapse; width: 100%; font-size: .92rem; }}
th, td {{ border: 1px solid var(--line); padding: 6px 8px; text-align: left; vertical-align: top; }}
p.home {{ color: var(--muted); }}
</style>
</head>
<body><main>
<p class="home"><a href="./">DIMA Now 내려받기</a></p>
{body}
</main></body>
</html>
"""


def inline(text: str) -> str:
    out = html.escape(text, quote=True)
    out = re.sub(r"&lt;(https://[^\s&]+)&gt;", r'<a href="\1">\1</a>', out)
    out = re.sub(r"\[([^\]]+)\]\((https://[^)\s]+)\)", r'<a href="\2">\1</a>', out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    return out


def render(markdown: str) -> str:
    blocks, paragraph, items, rows = [], [], [], []

    def flush():
        if paragraph:
            blocks.append("<p>" + inline(" ".join(paragraph)) + "</p>")
            paragraph.clear()
        if items:
            blocks.append("<ul>" + "".join(f"<li>{inline(i)}</li>" for i in items) + "</ul>")
            items.clear()
        if rows:
            header, body = rows[0], [r for r in rows[2:]]
            cells = lambda r, tag: "".join(f"<{tag}>{inline(c.strip())}</{tag}>" for c in r.strip().strip("|").split("|"))
            blocks.append('<div class="table"><table><thead><tr>' + cells(header, "th") + "</tr></thead><tbody>" +
                          "".join("<tr>" + cells(r, "td") + "</tr>" for r in body) + "</tbody></table></div>")
            rows.clear()

    for line in markdown.splitlines():
        stripped = line.strip()
        if not stripped:
            flush()
        elif stripped.startswith("|"):
            if paragraph or items:
                flush()
            rows.append(stripped)
        elif stripped.startswith("## "):
            flush()
            blocks.append(f"<h2>{inline(stripped[3:])}</h2>")
        elif stripped.startswith("# "):
            flush()
            blocks.append(f"<h1>{inline(stripped[2:])}</h1>")
        elif stripped.startswith("- "):
            if paragraph or rows:
                flush()
            items.append(stripped[2:])
        else:
            if items or rows:
                flush()
            paragraph.append(stripped)
    flush()
    return "\n".join(blocks)


def main() -> None:
    source, target = sys.argv[1], sys.argv[2]
    with open(source, encoding="utf-8") as f:
        body = render(f.read())
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write(PAGE.format(body=body))


if __name__ == "__main__":
    main()
