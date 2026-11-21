"""يبني مكتبة الخوادم data/proxy_library.zip من القوائم العامة: كل خادم = (العنوان، المنفذ، المفتاح، الدولة إن عُرفت).
التشغيل: python tools/build_proxy_library.py"""
import asyncio
import json
import socket
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import proxies  # noqa: E402


async def main():
    items = sorted(await proxies._fetch_sources())
    loop = asyncio.get_running_loop()
    sem = asyncio.Semaphore(100)

    async def one(k):
        async with sem:
            try:
                ip = (await loop.getaddrinfo(k[0], k[1], family=socket.AF_INET, type=socket.SOCK_STREAM))[0][4][0]
            except Exception:  # noqa: BLE001
                return k, None, None
            return k, ip, proxies._geolocate(ip)

    rows = await asyncio.gather(*[one(k) for k in items])
    out = [{"host": h, "port": p, "secret": s, "cc": cc} for (h, p, s), ip, cc in rows]
    dst = ROOT / "data" / "proxy_library.zip"
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("proxies.json", json.dumps(out, ensure_ascii=False))
    known = sum(1 for r in out if r["cc"])
    print(f"library: {len(out)} servers, country known for {known} -> {dst}")


asyncio.run(main())
