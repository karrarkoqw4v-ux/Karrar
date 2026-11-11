"""يبني كائنات الاستجابة بصيغة متوافقة مع OpenAI (chat.completion / chunk)، ليستخدمها أي تطبيق عميل جاهز."""
import time
import uuid


def new_id() -> str:
    return "chatcmpl-" + uuid.uuid4().hex[:12]


def completion(text: str, model: str = "bawsala") -> dict:
    return {
        "id": new_id(), "object": "chat.completion", "created": int(time.time()), "model": model,
        "choices": [{"index": 0, "message": {"role": "assistant", "content": text}, "finish_reason": "stop"}],
    }


def chunk(cid: str, piece: str, model: str = "bawsala", done: bool = False) -> dict:
    delta = {} if done else {"content": piece}
    return {
        "id": cid, "object": "chat.completion.chunk", "created": int(time.time()), "model": model,
        "choices": [{"index": 0, "delta": delta, "finish_reason": "stop" if done else None}],
    }
