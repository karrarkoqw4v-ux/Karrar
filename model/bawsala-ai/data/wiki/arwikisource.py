"""ويكي مصادر العربية: نصوص ومخطوطات في الملكية العامة أو مرخّصة."""
DATASET = "wikimedia/wikisource"
CONFIG = "20231101.ar"
LICENSE = "cc-by-sa-4.0"


def mission(max_tokens: int = 20_000_000) -> dict:
    return {"id": "arabic-wikisource", "kind": "hf_dataset", "name": DATASET, "config": CONFIG,
            "split": "train", "text_field": "text", "license": LICENSE, "max_tokens": max_tokens}
