"""يتأكد أن قواعد السياسة فعلية: ترخيص مسموح/ممنوع، ومنصة محظورة/مسموحة."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config.policy import host_ok, license_ok


def test_policy():
    assert license_ok("MIT")
    assert not license_ok("proprietary")
    assert not host_ok("www.tiktok.com")
    assert not host_ok("youtube.com")
    assert host_ok("en.wikipedia.org")
    print("نجح: فحوصات السياسة")


if __name__ == "__main__":
    test_policy()
