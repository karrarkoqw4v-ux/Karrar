"""سحب بروكسيات تيليجرام (MTProto) عامة، فحص اتصالها وسرعتها (زمن الاتصال)، وتصنيفها حسب الدولة.
القياس يتم من الجهاز الذي يشغّل البوت، لذلك يجب أن يكون له اتصال إنترنت مباشر (بدون وسيط) ليكون الزمن حقيقياً."""
import asyncio
import hashlib
import json
import logging
import os
import re
import socket
import time
import zipfile
from pathlib import Path

import aiohttp

from flagmap import FLAGS

log = logging.getLogger("proxies")

SOURCES = [
    "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt",
    "https://raw.githubusercontent.com/Grim1313/mtproto-for-telegram/master/all_proxies.txt",
    "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt",
]
JSON_SOURCES = ["https://mtpro.xyz/api/?type=mtproto"]  # قائمة إضافية (تعمل حيث الموقع متاح)
_RE = re.compile(r"server=([^&\s]+)&port=(\d+)&secret=([A-Za-z0-9_\-=%]+)")
REFRESH_EVERY = 900  # كل 15 دقيقة

LIB_PATH = Path(__file__).resolve().parent / "data" / "proxy_library.zip"


def load_library() -> list[dict]:
    """مكتبة الخوادم المضغوطة المرفقة مع البوت: [{host, port, secret, cc}]"""
    try:
        with zipfile.ZipFile(LIB_PATH) as z:
            return json.loads(z.read("proxies.json").decode("utf-8"))
    except Exception as e:  # noqa: BLE001
        log.warning("proxy library missing: %s", e)
        return []


def speed_pct(ms: float) -> int:
    """نسبة السرعة المقدّرة من زمن الاتصال: ≤20ms ≈ 96% ... ≥475ms ≈ 5%"""
    return int(max(5, min(99, round(100 - ms * 0.2))))


STATE: dict = {"ts": 0.0, "by_cc": {}, "all": {}, "alive": 0, "total": 0}
_lock = asyncio.Lock()
_geo = None


def key_of(host: str, port: int, secret: str) -> str:
    return hashlib.sha1(f"{host}:{port}:{secret}".encode()).hexdigest()[:8]


def link(e: dict) -> str:
    return f"https://t.me/proxy?server={e['host']}&port={e['port']}&secret={e['secret']}"


def _geolocate(ip: str) -> str | None:
    global _geo
    try:
        if _geo is None:
            from geoip2fast import GeoIP2Fast
            _geo = GeoIP2Fast()
        cc = _geo.lookup(ip).country_code
        return cc or None
    except Exception:  # noqa: BLE001
        return None


async def _fetch_sources() -> set[tuple[str, int, str]]:
    out: set[tuple[str, int, str]] = set()
    px = os.getenv("PROXY") or None
    timeout = aiohttp.ClientTimeout(total=40)
    async with aiohttp.ClientSession(timeout=timeout) as s:
        for u in SOURCES:
            try:
                async with s.get(u, proxy=px) as r:
                    txt = await r.text()
                for m in _RE.finditer(txt):
                    out.add((m.group(1).rstrip("."), int(m.group(2)), m.group(3)))
            except Exception as e:  # noqa: BLE001
                log.warning("proxy source failed %s: %s", u, e)
        for u in JSON_SOURCES:
            try:
                async with s.get(u, proxy=px) as r:
                    data = await r.json(content_type=None)
                for it in data if isinstance(data, list) else []:
                    h, pt, sec = it.get("host"), it.get("port"), it.get("secret")
                    if h and pt and sec:
                        out.add((str(h).rstrip("."), int(pt), str(sec)))
            except Exception as e:  # noqa: BLE001
                log.warning("proxy json source failed %s: %s", u, e)
    return out


async def probe(host: str, port: int, tries: int = 5) -> tuple[str, float] | None:
    """(عنوان IP، وسيط زمن اتصال TCP بالمللي ثانية) أو None. المستقر فقط: ينجح 4 من 5 اتصالات"""
    loop = asyncio.get_running_loop()
    try:
        ip = (await loop.getaddrinfo(host, port, family=socket.AF_INET, type=socket.SOCK_STREAM))[0][4][0]
    except Exception:  # noqa: BLE001
        return None
    times: list[float] = []
    for _ in range(tries):
        t = time.perf_counter()
        try:
            _, w = await asyncio.wait_for(asyncio.open_connection(ip, port), 4)
            times.append((time.perf_counter() - t) * 1000)
            w.close()
        except Exception:  # noqa: BLE001
            pass
        await asyncio.sleep(0.05)
    if len(times) < tries - 1:
        return None
    times.sort()
    return ip, times[len(times) // 2]


async def refresh() -> None:
    async with _lock:
        lib = load_library()
        lib_cc = {(r["host"].rstrip("."), int(r["port"]), r["secret"]): r.get("cc") for r in lib}
        items = set(lib_cc) | await _fetch_sources()
        sem = asyncio.Semaphore(80)

        async def one(k):
            async with sem:
                return k, await probe(k[0], k[1])

        res = await asyncio.gather(*[one(k) for k in items])
        alive = sum(1 for _, r in res if r)
        restricted = bool(items) and alive < 0.1 * len(items)  # الشبكة هنا لا تسمح بفحص حقيقي
        by_cc: dict[str, list] = {}
        allp: dict[str, dict] = {}
        for (host, port, secret), r in res:
            if r:
                ip, ms = r
                cc, ok = _geolocate(ip) or lib_cc.get((host, port, secret)), True
            else:
                ip, ms, cc, ok = "", None, lib_cc.get((host, port, secret)), False
                if not restricted:
                    continue  # شبكة سليمة والخادم لم يتصل ← نستبعده
            if cc not in FLAGS:
                continue
            e = {"key": key_of(host, port, secret), "host": host, "port": port, "secret": secret, "ip": ip, "cc": cc, "ms": ms, "ok": ok}
            allp[e["key"]] = e
            by_cc.setdefault(cc, []).append(e)
        for cc, v in by_cc.items():
            v.sort(key=lambda x: (not x["ok"], x["ms"] if x["ms"] is not None else 1e9))
            for n, e in enumerate(v, 1):
                e["name"] = f"خادم {FLAGS[cc][1]} {n}"
        STATE.update({"ts": time.time(), "by_cc": by_cc, "all": allp, "alive": alive, "total": len(items), "restricted": restricted})
        log.info("proxies: %d/%d connected, %d in %d countries (restricted=%s)", alive, len(items), len(allp), len(by_cc), restricted)


async def ensure_fresh() -> None:
    if time.time() - STATE["ts"] > REFRESH_EVERY or not STATE["ts"]:
        await refresh()


async def loop_forever() -> None:
    while True:
        try:
            await refresh()
        except Exception as e:  # noqa: BLE001
            log.warning("proxy refresh error: %s", e)
        await asyncio.sleep(REFRESH_EVERY)


def servers(cc: str) -> list[dict]:
    """كل السيرفرات المتصلة المستقرة لدولة (أي بنق) — الأسرع أولاً، حتى 10"""
    return STATE["by_cc"].get(cc, [])[:10]


def countries() -> list[tuple[str, int]]:
    """كل دول الأعلام (68): التي فيها سيرفرات مستقرة أولاً ثم الباقي أبجدياً — (الرمز، العدد)"""
    out = [(cc, len(STATE["by_cc"].get(cc, []))) for cc in FLAGS]
    out.sort(key=lambda x: (x[1] == 0, -x[1], FLAGS[x[0]][1]))
    return out
