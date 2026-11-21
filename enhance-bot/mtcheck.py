"""فحص حقيقي لخادم MTProto وسيط: نُنشئ اتصال obfuscated2 (أو Fake-TLS) ونرسل طلب req_pq_multi لتيليجرام عبره،
ونقيس زمن وصول الرد (resPQ). إن وصل الرد فالخادم يعمل فعلاً ويوصلك بتيليجرام — وليس مجرد منفذ مفتوح."""
import asyncio
import base64
import hashlib
import hmac
import os
import re
import struct
import time

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

_HEX = re.compile(r"^[0-9a-fA-F]+$")
_BAD_START = (b"HEAD", b"POST", b"GET ", b"OPTI", b"\xee\xee\xee\xee", b"\xdd\xdd\xdd\xdd", b"\x16\x03\x01\x02")


def parse_secret(s: str) -> tuple[str, bytes, bytes] | None:
    """(النوع plain|dd|ee ، المفتاح 16 بايت ، النطاق للـ ee) أو None"""
    raw = None
    s = s.strip()
    if _HEX.match(s) and len(s) % 2 == 0:
        raw = bytes.fromhex(s)
    else:
        try:
            raw = base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))
        except Exception:  # noqa: BLE001
            return None
    if len(raw) == 16:
        return "plain", raw, b""
    if len(raw) == 17 and raw[0] == 0xDD:
        return "dd", raw[1:], b""
    if len(raw) > 17 and raw[0] == 0xEE:
        return "ee", raw[1:17], raw[17:]
    return None


def _ctr(key: bytes, iv: bytes):
    return Cipher(algorithms.AES(key), modes.CTR(iv)).encryptor()


def _obf_init(secret: bytes, tag: bytes, dc: int = 2):
    while True:
        init = bytearray(os.urandom(64))
        if init[0] == 0xEF or bytes(init[:4]) in _BAD_START or bytes(init[4:8]) == b"\0\0\0\0":
            continue
        break
    init[56:60] = tag
    init[60:62] = struct.pack("<h", dc)
    kiv = bytes(init[8:56])
    rev = kiv[::-1]
    enc = _ctr(hashlib.sha256(kiv[:32] + secret).digest(), kiv[32:])
    dec = _ctr(hashlib.sha256(rev[:32] + secret).digest(), rev[32:])
    e = enc.update(bytes(init))
    return bytes(init[:56]) + e[56:64], enc, dec


def _req_pq() -> bytes:
    msg_id = (int(time.time()) << 32) & ~3
    body = struct.pack("<I", 0xBE7E8EF1) + os.urandom(16)  # req_pq_multi + nonce
    return b"\0" * 8 + struct.pack("<qI", msg_id, len(body)) + body  # 40 بايت


def _tls_hello(domain: bytes, secret: bytes) -> bytes:
    def ext(t: int, d: bytes) -> bytes:
        return struct.pack(">HH", t, len(d)) + d
    sni = ext(0, struct.pack(">HBH", len(domain) + 3, 0, len(domain)) + domain)
    exts = (sni + ext(0x17, b"") + ext(0xFF01, b"\x00") + ext(0x0A, struct.pack(">HHHH", 6, 0x1D, 0x17, 0x18)) + ext(0x0B, b"\x01\x00")
            + ext(0x23, b"") + ext(0x10, struct.pack(">H", 14) + b"\x02h2\x08http/1.1")
            + ext(0x0D, struct.pack(">H", 16) + bytes.fromhex("04030804040105030805050108060601"))
            + ext(0x33, struct.pack(">HHH", 36, 0x1D, 32) + os.urandom(32)) + ext(0x2D, b"\x01\x01") + ext(0x2B, b"\x04\x03\x04\x03\x03"))
    ciphers = bytes.fromhex("130113021303c02bc02fc02cc030cca9cca8")
    sess = os.urandom(32)

    def build(rnd: bytes) -> bytes:
        body = b"\x03\x03" + rnd + b"\x20" + sess + struct.pack(">H", len(ciphers)) + ciphers + b"\x01\x00" + struct.pack(">H", len(exts)) + exts
        hs = b"\x01" + struct.pack(">I", len(body))[1:] + body
        return b"\x16\x03\x01" + struct.pack(">H", len(hs)) + hs
    digest = hmac.new(secret, build(b"\0" * 32), hashlib.sha256).digest()
    ts = struct.pack("<I", int(time.time()))
    rnd = digest[:28] + bytes(a ^ b for a, b in zip(digest[28:], ts))
    return build(rnd)


async def _read_exact(r: asyncio.StreamReader, n: int, to: float) -> bytes:
    return await asyncio.wait_for(r.readexactly(n), to)


async def handshake(ip: str, port: int, secret_str: str, timeout: float = 6.0) -> float | None:
    """زمن (مللي ثانية) وصول رد تيليجرام عبر الخادم، أو None إن لم يعمل"""
    sec = parse_secret(secret_str)
    if not sec:
        return None
    kind, key, domain = sec
    w = None
    try:
        r, w = await asyncio.wait_for(asyncio.open_connection(ip, port), timeout)
        tag = b"\xef\xef\xef\xef" if kind == "plain" else b"\xdd\xdd\xdd\xdd"
        init, enc, dec = _obf_init(key, tag)
        payload = _req_pq()
        if kind == "plain":
            frame = bytes([len(payload) // 4]) + payload
        else:
            pad = os.urandom(os.urandom(1)[0] % 4)
            frame = struct.pack("<i", len(payload) + len(pad)) + payload + pad
        data = enc.update(frame)
        if kind == "ee":
            r_hello = _tls_hello(domain or b"www.google.com", key)
            w.write(r_hello)
            await w.drain()
            hdr = await _read_exact(r, 5, timeout)
            if hdr[0] != 0x16 or hdr[1] != 3:
                return None
            await _read_exact(r, struct.unpack(">H", hdr[3:5])[0], timeout)
            # نقرأ سجلات TLS التالية حتى أول بيانات تطبيق
            w.write(b"\x14\x03\x03\x00\x01\x01" + b"\x17\x03\x03" + struct.pack(">H", len(init) + len(data)) + init + data)
            await w.drain()
            t1 = time.perf_counter()
            buf = b""
            while True:
                h = await _read_exact(r, 5, timeout)
                body = await _read_exact(r, struct.unpack(">H", h[3:5])[0], timeout)
                if h[0] == 0x17:
                    buf += body
                    if len(buf) >= 5:
                        break
            plain = dec.update(buf)
            n = struct.unpack("<i", plain[:4])[0] & 0x7FFFFFFF
            return (time.perf_counter() - t1) * 1000 if n >= 40 else None
        t0 = time.perf_counter()
        w.write(init + data)
        await w.drain()
        first = await _read_exact(r, 1, timeout)
        if kind == "plain":
            ln = dec.update(first)[0]
            n = ln * 4 if ln < 127 else 0
            if ln >= 127:
                rest = dec.update(await _read_exact(r, 3, timeout))
                n = int.from_bytes(rest, "little") * 4
        else:
            rest = await _read_exact(r, 3, timeout)
            n = struct.unpack("<i", dec.update(first + rest))[0] & 0x7FFFFFFF
        return (time.perf_counter() - t0) * 1000 if n >= 40 else None
    except Exception:  # noqa: BLE001
        return None
    finally:
        if w:
            try:
                w.close()
            except Exception:  # noqa: BLE001
                pass


async def test(ip: str, port: int, secret: str, tries: int = 4) -> dict | None:
    """اختبار قوي: عدة محاولات handshake حقيقية ← تقييم 0..100 من (السرعة، الثبات، الاعتمادية)"""
    times: list[float] = []
    for _ in range(tries):
        t = await handshake(ip, port, secret)
        if t is not None:
            times.append(t)
        await asyncio.sleep(0.1)
    if len(times) < max(2, tries - 1):
        return None
    times.sort()
    med = times[len(times) // 2]
    jitter = times[-1] - times[0]
    lat = max(0.0, min(1.0, 1 - (med - 50) / 950))
    jit = max(0.0, min(1.0, 1 - jitter / 500))
    rel = len(times) / tries
    return {"ms": med, "jitter": jitter, "score": round(100 * (0.55 * lat + 0.2 * jit + 0.25 * rel))}
