"""توليد صور من نص عبر Pollinations (خدمة مجانية بلا مفتاح). يُرجع بايتات الصورة مباشرة، أو None عند الفشل."""
import urllib.parse

import requests


def generate(prompt: str, width: int = 1024, height: int = 1024) -> bytes:
    url = "https://image.pollinations.ai/prompt/" + urllib.parse.quote(prompt)
    try:
        r = requests.get(url, params={"width": width, "height": height, "nologo": "true"}, timeout=60)
        if r.status_code == 200 and r.headers.get("content-type", "").startswith("image/"):
            return r.content
    except Exception:
        pass
    return None
