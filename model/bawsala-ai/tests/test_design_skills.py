"""يتأكد أن كل أنواع التصاميم الـ12 موجودة ولها وصف بنية وجملة نظام صالحة."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from skills import design


def test_12_designs():
    names = [n for n in dir(design) if hasattr(getattr(design, n), "PAGE_TYPE")]
    assert len(names) == 12, f"المتوقع 12 تصميماً، الموجود {len(names)}"
    for n in names:
        mod = getattr(design, n)
        assert mod.NAME_AR and "اكتب صفحة" in mod.SYSTEM_PROMPT_ADDON


if __name__ == "__main__":
    test_12_designs()
    print("نجح: 12 نوع تصميم")
