"""ويكي كتب العربية: كتب تعليمية مفتوحة (نفس ترخيص ويكيميديا)."""
DATASET = "wikimedia/wikibooks"
CONFIG = "20231101.ar"
LICENSE = "cc-by-sa-4.0"


def mission(max_tokens: int = 30_000_000) -> dict:
    return {"id": "arabic-wikibooks", "kind": "hf_dataset", "name": DATASET, "config": CONFIG,
            "split": "train", "text_field": "text", "license": LICENSE, "max_tokens": max_tokens}
