"""حالة بسيطة محفوظة بملف JSON: قائمة المستخدمين وتاريخ محادثة كل واحد منهم، ووقت بدء التشغيل."""
import json
import os
import time

import config


class State:
    def __init__(self):
        self.start = time.time()
        self.data = self._load()

    def _load(self) -> dict:
        if os.path.exists(config.STATE_FILE):
            return json.load(open(config.STATE_FILE, encoding="utf-8"))
        return {"users": {}}

    def _save(self):
        json.dump(self.data, open(config.STATE_FILE, "w", encoding="utf-8"), ensure_ascii=False)

    def touch_user(self, chat_id: int, from_user: dict = None):
        u = self.data["users"].setdefault(str(chat_id), {
            "history": [], "mode": "idle", "model": None, "agreed": False,
            "stats": {"images": 0, "videos": 0, "games": 0, "apps": 0}, "first_seen": time.time(),
        })
        if from_user:
            u["name"] = from_user.get("first_name", "")
            u["username"] = from_user.get("username", "")
            u["is_premium"] = bool(from_user.get("is_premium"))
        self._save()

    def is_agreed(self, chat_id: int) -> bool:
        return bool(self.data["users"].get(str(chat_id), {}).get("agreed"))

    def set_agreed(self, chat_id: int):
        self.data["users"].setdefault(str(chat_id), {})["agreed"] = True
        self._save()

    def profile(self, chat_id: int) -> dict:
        return self.data["users"].get(str(chat_id), {})

    def bump_stat(self, chat_id: int, key: str):
        u = self.data["users"].setdefault(str(chat_id), {"stats": {}})
        u.setdefault("stats", {})
        u["stats"][key] = u["stats"].get(key, 0) + 1
        self._save()

    def get_mode(self, chat_id: int) -> dict:
        return self.data["users"].get(str(chat_id), {}).get("mode_data", {})

    def set_mode(self, chat_id: int, mode: str, **extra):
        u = self.data["users"].setdefault(str(chat_id), {"history": []})
        u["mode"] = mode
        u["mode_data"] = extra
        self._save()

    def current_mode(self, chat_id: int) -> str:
        return self.data["users"].get(str(chat_id), {}).get("mode", "idle")

    def set_model(self, chat_id: int, model: str):
        self.data["users"].setdefault(str(chat_id), {"history": []})["model"] = model
        self._save()

    def get_model(self, chat_id: int):
        return self.data["users"].get(str(chat_id), {}).get("model")

    def history(self, chat_id: int) -> list:
        return self.data["users"].get(str(chat_id), {}).get("history", [])

    def save_history(self, chat_id: int, history: list):
        self.data["users"].setdefault(str(chat_id), {})["history"] = history[-20:]
        self._save()

    def all_users(self) -> list:
        return [int(k) for k in self.data["users"]]

    def user_count(self) -> int:
        return len(self.data["users"])

    def uptime(self) -> str:
        s = int(time.time() - self.start)
        h, m = s // 3600, (s % 3600) // 60
        return f"{h} ساعة و{m} دقيقة" if h else f"{m} دقيقة"
