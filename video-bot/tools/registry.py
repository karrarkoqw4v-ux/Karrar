"""سجل الأدوات: كل أداة تُعرَّف بـ @tool وتُعرض تلقائياً في قائمة البوت."""
from __future__ import annotations

import asyncio
import inspect
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Awaitable, Callable

CATS: dict[str, tuple[str, str]] = {
    "text": ("📝", "النصوص والزخرفة"),
    "calc": ("🧮", "الحاسبات والتواريخ"),
    "encode": ("🔐", "التشفير والتحويل"),
    "image": ("🖼", "أدوات الصور"),
    "pdf": ("📄", "PDF والملفات"),
    "media": ("🎬", "الفيديو والصوت"),
    "net": ("🌐", "الإنترنت والمعلومات"),
    "misc": ("🎲", "أدوات متنوعة"),
}


@dataclass
class Out:
    kind: str  # text | photo | doc | video | audio | note | sticker | anim
    data: Any = None  # نص أو مسار/بايتات
    name: str = ""
    caption: str = ""

    @staticmethod
    def text(t: str) -> "Out":
        return Out("text", t)

    @staticmethod
    def photo(p, caption: str = "") -> "Out":
        return Out("photo", p, caption=caption)

    @staticmethod
    def doc(p, name: str = "", caption: str = "") -> "Out":
        return Out("doc", p, name, caption)

    @staticmethod
    def video(p, caption: str = "") -> "Out":
        return Out("video", p, caption=caption)

    @staticmethod
    def audio(p, name: str = "", caption: str = "") -> "Out":
        return Out("audio", p, name, caption)

    @staticmethod
    def note(p) -> "Out":
        return Out("note", p)

    @staticmethod
    def sticker(p) -> "Out":
        return Out("sticker", p)

    @staticmethod
    def anim(p, caption: str = "") -> "Out":
        return Out("anim", p, caption=caption)


class Ask(Exception):
    """تطلب الأداة معلومة إضافية من المستخدم (خطوة ثانية)"""

    def __init__(self, prompt: str, state: dict | None = None):
        self.prompt = prompt
        self.state = state or {}


class ToolError(Exception):
    """رسالة خطأ تُعرض للمستخدم كما هي"""


@dataclass
class Ctx:
    uid: int
    workdir: Path
    text: str = ""
    path: str | None = None
    paths: list[str] = field(default_factory=list)
    opt: str = ""
    state: dict = field(default_factory=dict)
    mime: str = ""
    name: str = ""


@dataclass
class Tool:
    id: str
    cat: str
    title: str
    hint: str
    fn: Callable[[Ctx], Any]
    inp: str = "text"  # text|photo|video|audio|pdf|file|none|photos|pdfs|audios|media
    opts: list[tuple[str, str]] = field(default_factory=list)
    needs_net: bool = False
    sample: Any = None
    auto: tuple = ()  # قيم الخيارات التي تُنفَّذ فوراً بدون مدخلات


TOOLS: dict[str, Tool] = {}


def tool(id: str, cat: str, title: str, hint: str, inp: str = "text", opts: list[tuple[str, str]] | None = None, net: bool = False, sample: Any = None, auto: tuple = ()):
    def deco(fn):
        TOOLS[id] = Tool(id, cat, title, hint, fn, inp, opts or [], net, sample, auto)
        return fn
    return deco


def by_cat(cat: str) -> list[Tool]:
    return [t for t in TOOLS.values() if t.cat == cat]


async def run_tool(t: Tool, ctx: Ctx) -> list[Out]:
    if inspect.iscoroutinefunction(t.fn):
        res = await t.fn(ctx)
    else:
        res = await asyncio.to_thread(t.fn, ctx)
    if res is None:
        return []
    if isinstance(res, (str, Out)):
        res = [res]
    return [Out.text(r) if isinstance(r, str) else r for r in res]
