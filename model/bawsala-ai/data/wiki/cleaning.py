"""تنظيف نصوص ويكيميديا: إزالة قوالب [[...]] والفهارس وعلامات التنسيق قبل التوكنة."""
import re

WIKI_MARKUP = re.compile(r"\{\{.*?\}\}|\[\[([^|\]]*\|)?([^\]]*)\]\]|'''|''")
MULTI_NEWLINE = re.compile(r"\n{3,}")


def clean(text: str) -> str:
    text = WIKI_MARKUP.sub(lambda m: m.group(2) or "", text)
    text = MULTI_NEWLINE.sub("\n\n", text)
    return text.strip()
