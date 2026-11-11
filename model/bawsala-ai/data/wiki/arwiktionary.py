"""قاموس ويكيميديا العربي: تعريفات كلمات ومعاني، يفيد الفهم المعجمي."""
DATASET = "wikimedia/wiktionary"
CONFIG = "20231101.ar"
LICENSE = "cc-by-sa-4.0"


def mission(max_tokens: int = 10_000_000) -> dict:
    return {"id": "arabic-wiktionary", "kind": "hf_dataset", "name": DATASET, "config": CONFIG,
            "split": "train", "text_field": "text", "license": LICENSE, "max_tokens": max_tokens}
