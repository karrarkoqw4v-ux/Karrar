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
    r = requests.get(f"{config.API}/getUpdates", params={"offset": offset, "timeout": timeout,
                      "allowed_updates": ["message", "callback_query"]}, timeout=timeout + 10)
    data = r.json()
    return data.get("result", []) if data.get("ok") else []


def send_message(chat_id: int, text: str, entities: list = None, reply_to: int = None, reply_markup: dict = None) -> dict:
    payload = {"chat_id": chat_id, "text": text}
    if entities:
        payload["entities"] = entities
    if reply_to:
        payload["reply_to_message_id"] = reply_to
    if reply_markup:
        payload["reply_markup"] = reply_markup
    return call("sendMessage", **payload)


def send_photo_bytes(chat_id: int, photo_bytes: bytes, caption: str = "") -> dict:
    r = requests.post(f"{config.API}/sendPhoto", data={"chat_id": chat_id, "caption": caption},
                       files={"photo": ("image.jpg", photo_bytes, "image/jpeg")}, timeout=60)
    data = r.json()
    if not data.get("ok"):
        raise RuntimeError(f"sendPhoto failed: {data}")
    return data["result"]


def edit_message_reply_markup(chat_id: int, message_id: int, reply_markup: dict) -> dict:
    return call("editMessageReplyMarkup", chat_id=chat_id, message_id=message_id, reply_markup=reply_markup)


def answer_callback(callback_id: str, text: str = "") -> dict:
    return call("answerCallbackQuery", callback_query_id=callback_id, text=text)


def set_my_commands(commands: list) -> dict:
    return call("setMyCommands", commands=commands)
