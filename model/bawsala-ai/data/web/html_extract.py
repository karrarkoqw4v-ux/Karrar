"""يستخرج النص القابل للقراءة من HTML بدون مكتبات خارجية (html.parser من المكتبة القياسية)."""
from html.parser import HTMLParser

SKIP_TAGS = {"script", "style", "nav", "footer", "header", "noscript"}


class _TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.skip_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag in SKIP_TAGS:
            self.skip_depth += 1

    def handle_endtag(self, tag):
        if tag in SKIP_TAGS and self.skip_depth > 0:
            self.skip_depth -= 1

    def handle_data(self, data):
        if self.skip_depth == 0 and data.strip():
            self.parts.append(data.strip())


def extract_text(html: str) -> str:
    p = _TextExtractor()
    p.feed(html)
    return "\n".join(p.parts)
