"""سياسة جمع البيانات: ما المسموح به وما الممنوع، في مكان واحد يراجعه أي ملف قبل أن يجلب بيانات."""

ALLOWED_LICENSES = {
    "cc-by-sa-4.0", "cc-by-4.0", "cc0-1.0", "mit", "apache-2.0",
    "bsd-3-clause", "bsd-2-clause", "public-domain", "own",
}

# يُمنع جلب بيانات من هذه المنصات مهما كان السبب (شروط الاستخدام تمنع الزحف الآلي)
BLOCKED_PLATFORMS = {
    "youtube.com", "youtu.be", "tiktok.com", "instagram.com",
    "facebook.com", "twitter.com", "x.com", "snapchat.com",
}

ALLOWED_SCHEMES = {"https"}
MAX_PAGES_PER_RUN = 100
REQUEST_DELAY_SEC = 3
RESPECT_ROBOTS_TXT = True


def license_ok(license_id: str) -> bool:
    return license_id.lower() in ALLOWED_LICENSES


def host_ok(host: str) -> bool:
    return not any(b in host.lower() for b in BLOCKED_PLATFORMS)
