"""المرحلة 2 (قبل prep_data): يشغّل كل مهمات جمع البيانات بالترتيب (ويكيبيديا، ثم كل لغة كود، ثم الويب)،
يتحقق من السياسة في config/policy.py، ويتخطى أي مهمة فاشلة بدل إيقاف الباقي.
    python train/06_data_missions.py --out data/raw
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config.policy import license_ok  # noqa: E402
from data.wiki.mission import all_missions as wiki_missions  # noqa: E402
import data.code as code_pkg  # noqa: E402


def collect_missions():
    missions = wiki_missions()
    for name in dir(code_pkg):
        mod = getattr(code_pkg, name)
        if hasattr(mod, "mission"):
            missions.append(mod.mission())
    return missions


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="data/raw")
    a = ap.parse_args()

    try:
        from datasets import load_dataset
    except ImportError:
        print("حزمة datasets غير مثبَّتة؛ هذا السكربت يحتاج `pip install datasets` ليجلب فعلياً، سأطبع الخطة فقط.")
        load_dataset = None

    for m in collect_missions():
        if not license_ok(m["license"]):
            print(f"تخطّي {m['id']}: ترخيص غير مسموح ({m['license']})")
            continue
        out_dir = os.path.join(a.out, m["id"])
        os.makedirs(out_dir, exist_ok=True)
        if load_dataset is None:
            print(f"[خطة فقط] {m['id']} <- {m.get('name')} حتى {m.get('max_tokens', 0):,} توكن")
            continue
        try:
            kw = {"data_dir": m["data_dir"]} if m.get("data_dir") else {}
            ds = load_dataset(m["name"], m.get("config"), split=m.get("split", "train"), streaming=True, **kw)
            with open(os.path.join(out_dir, "part-00000.txt"), "w", encoding="utf-8") as f:
                for i, row in enumerate(ds):
                    f.write((row.get(m.get("text_field", "text"), "") or "") + "\n\n")
                    if i > 2000:  # حد أمان لكل مهمة؛ ارفعه عند التدريب الحقيقي
                        break
            print(f"تم {m['id']}")
        except Exception as e:
            print(f"فشلت {m['id']}: {e}")


if __name__ == "__main__":
    main()
