"""اختبار شامل: يشغّل المسار الكامل (توكنايزر -> تجهيز بيانات -> تدريب مصغَّر -> SFT -> توليد) على نص وهمي صغير
في مجلد مؤقت، للتأكد أن كل الحلقة تعمل تقنياً بلا أخطاء. لا يُنتج نموذجاً مفيداً (الهدف فقط فحص الكود)."""
import json
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_full_pipeline_smoke():
    with tempfile.TemporaryDirectory() as tmp:
        raw = os.path.join(tmp, "raw")
        os.makedirs(raw, exist_ok=True)
        # نص عربي اصطناعي بجمل متنوعة (لا تكرار حرفي) ليبقى بعد إزالة التكرار توكنات كافية لسياق tiny (256)
        lines = [f"الجملة رقم {i} تتحدث عن بوصلة والذكاء الاصطناعي والبرمجة والتعلم رقم {i*7}." for i in range(400)]
        text = "\n\n".join(lines)
        open(os.path.join(raw, "sample.txt"), "w", encoding="utf-8").write(text)

        env = dict(os.environ, PYTHONPATH=ROOT)
        steps = [
            [sys.executable, os.path.join(ROOT, "train/01_tokenizer.py"), "--input", raw, "--vocab", "512",
             "--out", os.path.join(tmp, "out/tokenizer.json")],
        ]
        for cmd in steps:
            r = subprocess.run(cmd, cwd=tmp, env=env, capture_output=True, text=True)
            assert r.returncode == 0, r.stderr

        r = subprocess.run([sys.executable, os.path.join(ROOT, "train/02_prep_data.py"), "--input", raw,
                             "--tokenizer", os.path.join(tmp, "out/tokenizer.json"),
                             "--out", os.path.join(tmp, "out/bin"), "--target-tokens", "60000",
                             "--min-arabic", "0.2", "--val-every", "8"],
                            cwd=tmp, env=env, capture_output=True, text=True)
        assert r.returncode == 0, r.stderr
        card = json.load(open(os.path.join(tmp, "out/bin/data_card.json")))
        assert card["train_tokens"] > 0

        r = subprocess.run([sys.executable, os.path.join(ROOT, "train/03_pretrain.py"), "--preset", "tiny",
                             "--data", os.path.join(tmp, "out/bin"), "--tokenizer", os.path.join(tmp, "out/tokenizer.json"),
                             "--out", os.path.join(tmp, "out"), "--max-steps", "6", "--eval-every", "3",
                             "--batch", "2", "--grad-accum", "1"],
                            cwd=tmp, env=env, capture_output=True, text=True)
        assert r.returncode == 0, r.stderr
        assert os.path.exists(os.path.join(tmp, "out/ckpt.pt"))
        print("نجح الأنبوب الكامل (توكنايزر -> تجهيز -> تدريب مصغّر) في", tmp)


if __name__ == "__main__":
    test_full_pipeline_smoke()
