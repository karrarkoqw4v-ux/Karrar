"""يتأكد أن جامع الويب يرفض http والمنصات المحظورة، ويستخرج النص من HTML بصورة سليمة."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from data.web.fetch import fetch_page
from data.web.html_extract import extract_text


def test_rejects_http_and_blocked():
    r1 = fetch_page("http://example.com/")
    assert not r1["ok"] and "https" in r1["reason"]
    r2 = fetch_page("https://www.instagram.com/x")
    assert not r2["ok"]


def test_html_extract():
    html = "<html><script>bad()</script><body><p>مرحبا</p><style>.x{}</style></body></html>"
    assert extract_text(html) == "مرحبا"


if __name__ == "__main__":
    test_rejects_http_and_blocked()
    test_html_extract()
    print("نجح: جامع الويب")
