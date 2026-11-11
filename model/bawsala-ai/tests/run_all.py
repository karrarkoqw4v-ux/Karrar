"""يشغّل كل اختبارات tests/ بالترتيب ويطبع ملخصاً صادقاً (نجح/فشل)، بدون إطار اختبار خارجي.
    python tests/run_all.py
"""
import importlib
import os
import sys
import traceback

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

MODULES = [
    "tests.test_core_model",
    "tests.test_policy",
    "tests.test_web_fetch",
    "tests.test_dialects",
    "tests.test_code_languages",
    "tests.test_qa_data",
    "tests.test_design_skills",
    "tests.test_repo_skills",
    "tests.test_pipeline_smoke",
]


def main():
    passed, failed = [], []
    for name in MODULES:
        mod = importlib.import_module(name)
        funcs = [getattr(mod, f) for f in dir(mod) if f.startswith("test_") and callable(getattr(mod, f))]
        for f in funcs:
            label = f"{name}.{f.__name__}"
            try:
                f()
                passed.append(label)
                print("✅", label)
            except Exception as e:
                failed.append((label, str(e)))
                print("❌", label, "-", e)
                traceback.print_exc(limit=1)
    print(f"\n{len(passed)} نجح، {len(failed)} فشل، من أصل {len(passed) + len(failed)}")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
