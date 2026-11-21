"""اختبار فحص MTProto مقابل خادم وسيط وهمي محلي (يُنفّذ منطق الخادم من جهته: فكّ التشفير والتحقق من الوسم والرد)"""
import asyncio
import hashlib
import os
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import mtcheck  # noqa: E402


async def run_server(secret: bytes, kind: str, wrong_secret: bool = False):
    got = {}

    async def handle(r, w):
        init = await r.readexactly(64)
        kiv = init[8:56]
        sec = os.urandom(16) if wrong_secret else secret
        dec = mtcheck._ctr(hashlib.sha256(kiv[:32] + sec).digest(), kiv[32:])
        plain = dec.update(init)
        tag = plain[56:60]
        got["tag"] = tag
        want = b"\xef\xef\xef\xef" if kind == "plain" else b"\xdd\xdd\xdd\xdd"
        if tag != want:
            w.close()
            return
        rev = kiv[::-1]
        enc = mtcheck._ctr(hashlib.sha256(rev[:32] + sec).digest(), rev[32:])
        if kind == "plain":
            ln = dec.update(await r.readexactly(1))[0] * 4
        else:
            ln = struct.unpack("<i", dec.update(await r.readexactly(4)))[0] & 0x7FFFFFFF
        body = dec.update(await r.readexactly(ln))
        got["len"], got["ctor"] = ln, body[20:24]
        resp = b"\0" * 8 + b"\1" * 8 + struct.pack("<I", 60) + os.urandom(60)  # يشبه resPQ
        frame = (bytes([len(resp) // 4]) if kind == "plain" else struct.pack("<i", len(resp))) + resp
        w.write(enc.update(frame))
        await w.drain()
        await asyncio.sleep(0.2)
        w.close()

    srv = await asyncio.start_server(handle, "127.0.0.1", 0)
    return srv, srv.sockets[0].getsockname()[1], got


async def main():
    key = os.urandom(16)
    for kind, sstr in (("plain", key.hex()), ("dd", "dd" + key.hex())):
        srv, port, got = await run_server(key, kind)
        async with srv:
            ms = await mtcheck.handshake("127.0.0.1", port, sstr)
            assert ms is not None, kind
            assert got["ctor"] == struct.pack("<I", 0xBE7E8EF1) and 40 <= got["len"] <= 43, got
            res = await mtcheck.test("127.0.0.1", port, sstr, tries=3)
            assert res and res["score"] > 80, res
    # مفتاح خاطئ ← يفشل
    srv, port, _ = await run_server(key, "dd", wrong_secret=True)
    async with srv:
        assert await mtcheck.handshake("127.0.0.1", port, "dd" + key.hex(), timeout=2) is None
    # base64 secret
    import base64
    assert mtcheck.parse_secret(base64.urlsafe_b64encode(key).decode().rstrip("="))[0] == "plain"
    assert mtcheck.parse_secret("dd" + key.hex())[0] == "dd"
    assert mtcheck.parse_secret("ee" + key.hex() + b"www.google.com".hex())[2] == b"www.google.com"
    print("OK mtcheck")


asyncio.run(main())
