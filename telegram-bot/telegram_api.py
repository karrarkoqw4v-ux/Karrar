"""طبقة رقيقة فوق Telegram Bot API (طلبات HTTP مباشرة، بلا أطر عمل ثقيلة، لتبسيط النشر والاختبار)."""
import requests

import config


def call(method: str, **params) -> dict:
    r = requests.post(f"{config.API}/{method}", json=params, timeout=30)
    data = r.json()
    if not data.get("ok"):
        raise RuntimeError(f"{method} failed: {data}")
    return data["result"]


def get_updates(offset: int, timeout: int = 25) -> list:
    r = requests.get(f"{config.API}/getUpdates", params={"offset": offset, "timeout": timeout}, timeout=timeout + 10)
    data = r.json()
    return data.get("result", []) if data.get("ok") else []


def send_message(chat_id: int, text: str, entities: list = None, reply_to: int = None) -> dict:
    payload = {"chat_id": chat_id, "text": text}
    if entities:
        payload["entities"] = entities
    if reply_to:
        payload["reply_to_message_id"] = reply_to
    return call("sendMessage", **payload)


def set_my_commands(commands: list) -> dict:
    return call("setMyCommands", commands=commands)
