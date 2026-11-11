"""يشغّل المراحل كلها بالترتيب بأمر واحد (للتجربة السريعة على بيانات صغيرة؛ للتدريب الحقيقي شغّل كل مرحلة بنفسك على GPU).
    python train/10_pipeline.py --preset tiny --steps 60
"""
import argparse
import subprocess
import sys


def run(cmd):
    print("+", " ".join(cmd))
    r = subprocess.run([sys.executable] + cmd)
    if r.returncode != 0:
        raise SystemExit(f"فشلت الخطوة: {' '.join(cmd)}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preset", default="tiny")
    ap.add_argument("--steps", type=int, default=60)
    ap.add_argument("--data-dir", default="data/raw")
    a = ap.parse_args()

    run(["train/01_tokenizer.py", "--input", a.data_dir, "--vocab", "2048" if a.preset == "tiny" else "32000"])
    run(["train/02_prep_data.py", "--input", a.data_dir, "--target-tokens", "200000", "--min-arabic", "0.2"])
    run(["train/03_pretrain.py", "--preset", a.preset, "--max-steps", str(a.steps),
         "--eval-every", str(max(10, a.steps // 2)), "--batch", "4", "--grad-accum", "1"])
    run(["train/04_build_sft.py"])
    run(["train/05_sft.py", "--epochs", "2", "--batch", "8"])
    print("انتهى المسار الكامل. النموذج في out/chat.pt")


if __name__ == "__main__":
    main()
