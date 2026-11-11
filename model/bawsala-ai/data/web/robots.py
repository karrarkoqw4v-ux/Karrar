"""يتحقق من robots.txt قبل أي طلب، ويحترم Disallow. يُستخدم بواسطة fetch.py قبل كل صفحة."""
import urllib.robotparser
from urllib.parse import urlparse


def allowed(url: str, user_agent: str = "BawsalaAgent") -> bool:
    try:
        p = urlparse(url)
        rp = urllib.robotparser.RobotFileParser()
        rp.set_url(f"{p.scheme}://{p.netloc}/robots.txt")
        rp.read()
        return rp.can_fetch(user_agent, url)
    except Exception:
        return False  # عند الشك، لا تجلب الصفحة
