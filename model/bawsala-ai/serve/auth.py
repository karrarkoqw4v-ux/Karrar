"""تحقق بسيط من مفتاح الوصول للخادم المحلي (Authorization: Bearer <key>)؛ فارغ = بلا مفتاح."""


def check(auth_header: str, expected_key: str) -> bool:
    if not expected_key:
        return True
    return auth_header == "Bearer " + expected_key
