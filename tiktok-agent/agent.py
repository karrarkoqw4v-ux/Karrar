"""Bawsala TikTok assistant: writes scripts with an LLM and uploads a finished video to the user's TikTok *drafts* via the official Content Posting API.
No passwords, no browser automation, no auto-posting. Untested in the authoring sandbox (no network)."""
import json, os, sys, math, pathlib, urllib.parse
import requests

TOKENS = pathlib.Path("tokens.json")
OUT = pathlib.Path("out")
CK, CS = os.environ.get("TIKTOK_CLIENT_KEY", ""), os.environ.get("TIKTOK_CLIENT_SECRET", "")
REDIRECT = os.environ.get("TIKTOK_REDIRECT", "http://localhost:8080/callback")

PERSONA = ("أنت «بوصلة»، ذكاء اصطناعي ودود ومحترم تتكلم بالعراقي البسيط وتعرّف عن نفسك أنك ذكاء اصطناعي. "
           "لا تشتم ولا تعد بالأرباح ولا تقدّم نصيحة طبية أو قانونية نهائية. اكتب بصيغة المتكلم (أنا).")

def need_keys():
    if not CK or not CS:
        sys.exit("Set TIKTOK_CLIENT_KEY and TIKTOK_CLIENT_SECRET in the environment.")

def plan():
    base, key, model = os.environ.get("LLM_BASE_URL"), os.environ.get("LLM_API_KEY"), os.environ.get("LLM_MODEL")
    if not (base and key and model):
        sys.exit("Set LLM_BASE_URL, LLM_API_KEY, LLM_MODEL")
    OUT.mkdir(exist_ok=True)
    qs = [q.strip() for q in pathlib.Path("questions.txt").read_text(encoding="utf-8").splitlines() if q.strip()]
    for i, q in enumerate(qs, 1):
        prompt = (f"سؤال متابع: «{q}»\nاكتب JSON فقط بالمفاتيح: hook (جملة أولى جذابة تحت 8 كلمات)، script (نص 15-25 ثانية)، "
                  "caption (وصف تحت 150 حرفاً)، hashtags (5 هاشتاقات). في السكربت اعترف بأنك ذكاء اصطناعي.")
        r = requests.post(base.rstrip("/") + "/chat/completions", headers={"Authorization": "Bearer " + key},
                          json={"model": model, "messages": [{"role": "system", "content": PERSONA}, {"role": "user", "content": prompt}],
                                "temperature": 0.7}, timeout=90)
        r.raise_for_status()
        (OUT / f"{i:02d}.json").write_text(r.json()["choices"][0]["message"]["content"], encoding="utf-8")
        print("wrote", OUT / f"{i:02d}.json")

def auth_url():
    need_keys()
    q = urllib.parse.urlencode({"client_key": CK, "scope": "user.info.basic,video.upload", "response_type": "code",
                                "redirect_uri": REDIRECT, "state": "bawsala"})
    print("Open this URL, approve, then copy the 'code' value from the redirected address:\nhttps://www.tiktok.com/v2/auth/authorize/?" + q)

def exchange(code):
    need_keys()
    r = requests.post("https://open.tiktokapis.com/v2/oauth/token/",
                      data={"client_key": CK, "client_secret": CS, "code": urllib.parse.unquote(code),
                            "grant_type": "authorization_code", "redirect_uri": REDIRECT},
                      headers={"Content-Type": "application/x-www-form-urlencoded"}, timeout=30)
    r.raise_for_status()
    TOKENS.write_text(json.dumps(r.json()), encoding="utf-8")
    TOKENS.chmod(0o600)
    print("saved tokens.json (keep it private)")

def draft(path):
    tok = json.loads(TOKENS.read_text())["access_token"]
    p = pathlib.Path(path)
    size = p.stat().st_size
    chunk = min(size, 10_000_000)
    total = math.ceil(size / chunk)
    r = requests.post("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/",
                      headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json; charset=UTF-8"},
                      json={"source_info": {"source": "FILE_UPLOAD", "video_size": size, "chunk_size": chunk, "total_chunk_count": total}}, timeout=30)
    r.raise_for_status()
    data = r.json()["data"]
    url = data["upload_url"]
    with open(p, "rb") as f:
        for i in range(total):
            start = i * chunk
            buf = f.read(chunk)
            end = start + len(buf) - 1
            requests.put(url, data=buf, timeout=300, headers={"Content-Type": "video/mp4", "Content-Length": str(len(buf)),
                         "Content-Range": f"bytes {start}-{end}/{size}"}).raise_for_status()
    print("Uploaded to your TikTok drafts. Open TikTok, review the video, enable the AI-generated label, then post.")

if __name__ == "__main__":
    a = sys.argv[1:] or ["help"]
    {"plan": plan, "auth-url": auth_url, "exchange": lambda: exchange(a[1]), "draft": lambda: draft(a[1])}.get(a[0], lambda: print(__doc__))()
