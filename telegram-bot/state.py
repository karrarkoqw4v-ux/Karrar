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

    def touch_user(self, chat_id: int):
        self.data["users"].setdefault(str(chat_id), {"history": []})
        self._save()

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
