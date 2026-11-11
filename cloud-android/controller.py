"""Tiny HTTP controller that lets an AI agent drive an Android emulator through ADB.
Run: API_KEY=secret uvicorn controller:app --host 0.0.0.0 --port 8080   (untested in the authoring sandbox)"""
import base64, os, subprocess, tempfile
from fastapi import FastAPI, Header, HTTPException, UploadFile, File
from pydantic import BaseModel

ADB_TARGET = os.environ.get("ADB_TARGET", "127.0.0.1:5555")
API_KEY = os.environ.get("API_KEY", "")
app = FastAPI(title="Bawsala cloud Android")

def adb(*args, text=True, timeout=30):
    return subprocess.run(["adb", "-s", ADB_TARGET, *args], capture_output=True, text=text, timeout=timeout)

def auth(key):
    if not API_KEY or key != API_KEY:
        raise HTTPException(401, "bad key")

@app.on_event("startup")
def connect():
    subprocess.run(["adb", "connect", ADB_TARGET], capture_output=True)

class Tap(BaseModel):
    x: int
    y: int
class Swipe(BaseModel):
    x1: int; y1: int; x2: int; y2: int; ms: int = 300
class Text(BaseModel):
    text: str
class Pkg(BaseModel):
    package: str
class Key(BaseModel):
    key: str  # e.g. HOME, BACK, ENTER

@app.get("/screenshot")
def screenshot(x_api_key: str = Header("")):
    auth(x_api_key)
    r = adb("exec-out", "screencap", "-p", text=False)
    return {"png_base64": base64.b64encode(r.stdout).decode()}

@app.post("/tap")
def tap(b: Tap, x_api_key: str = Header("")):
    auth(x_api_key); adb("shell", "input", "tap", str(b.x), str(b.y)); return {"ok": True}

@app.post("/swipe")
def swipe(b: Swipe, x_api_key: str = Header("")):
    auth(x_api_key); adb("shell", "input", "swipe", *map(str, (b.x1, b.y1, b.x2, b.y2, b.ms))); return {"ok": True}

@app.post("/type")
def type_text(b: Text, x_api_key: str = Header("")):
    auth(x_api_key); adb("shell", "input", "text", b.text.replace(" ", "%s")); return {"ok": True}

@app.post("/key")
def key(b: Key, x_api_key: str = Header("")):
    auth(x_api_key); adb("shell", "input", "keyevent", "KEYCODE_" + b.key.upper()); return {"ok": True}

@app.post("/launch")
def launch(b: Pkg, x_api_key: str = Header("")):
    auth(x_api_key); adb("shell", "monkey", "-p", b.package, "-c", "android.intent.category.LAUNCHER", "1"); return {"ok": True}

@app.post("/install")
async def install(apk: UploadFile = File(...), x_api_key: str = Header("")):
    auth(x_api_key)
    with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as f:
        f.write(await apk.read()); path = f.name
    r = adb("install", "-r", path, timeout=180)
    return {"out": r.stdout + r.stderr}
