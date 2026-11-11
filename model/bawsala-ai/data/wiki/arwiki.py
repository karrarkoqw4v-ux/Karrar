"""ويكيبيديا العربية (CC BY-SA 4.0): أكبر مصدر نصوص عربية عامة مرخّصة."""
DATASET = "wikimedia/wikipedia"
CONFIG = "20231101.ar"
LICENSE = "cc-by-sa-4.0"
NOTE = "CC BY-SA تلزم ذكر المصدر والمشاركة بالمثل عند إعادة النشر."


def mission(max_tokens: int = 400_000_000) -> dict:
    return {"id": "arabic-wikipedia", "kind": "hf_dataset", "name": DATASET, "config": CONFIG,
            "split": "train", "text_field": "text", "license": LICENSE, "max_tokens": max_tokens}
