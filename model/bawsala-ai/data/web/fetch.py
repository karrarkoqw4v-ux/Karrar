"""يجلب صفحة واحدة بأمان: https فقط، منصات محظورة تُرفض، يحترم robots.txt، لا يتبع إعادة التوجيه عبر نطاقات أخرى."""
import time
import urllib.request
from urllib.parse import urlparse

from config.policy import ALLOWED_SCHEMES, REQUEST_DELAY_SEC, host_ok

from .robots import allowed as robots_allowed
from .html_extract import extract_text

_last_request = 0.0


def fetch_page(url: str, timeout: int = 15) -> dict:
    """يُرجع {"ok": True, "text": ...} أو {"ok": False, "reason": ...}. لا يرفع استثناء أبداً."""
    global _last_request
    p = urlparse(url)
    if p.scheme not in ALLOWED_SCHEMES:
        return {"ok": False, "reason": f"المخطط {p.scheme} غير مسموح (https فقط)"}
    if not host_ok(p.netloc):
        return {"ok": False, "reason": "هذا الموقع ضمن المنصات المحظورة بالسياسة"}
    if not robots_allowed(url):
        return {"ok": False, "reason": "robots.txt يمنع جلب هذه الصفحة"}
    elapsed = time.time() - _last_request
    if elapsed < REQUEST_DELAY_SEC:
        time.sleep(REQUEST_DELAY_SEC - elapsed)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "BawsalaAgent/1.0 (+policy-compliant)"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if resp.geturl().split("/")[2] != p.netloc:
                return {"ok": False, "reason": "رُفض بسبب إعادة توجيه لنطاق مختلف"}
            html = resp.read(2_000_000).decode("utf-8", errors="ignore")
    except Exception as e:
        return {"ok": False, "reason": str(e)}
    finally:
        _last_request = time.time()
    return {"ok": True, "text": extract_text(html)}
