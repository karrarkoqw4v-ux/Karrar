"""يتأكد أن كل اللهجات الـ28 محمَّلة وفيها الحقول المطلوبة، وأن كشف اللهجة العراقية يعمل."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from data.dialects import ALL, iraq


def test_all_loaded():
    assert len(ALL) == 28, f"المتوقع 28 لهجة، الموجود {len(ALL)}"
    for name in ALL:
        mod = __import__(f"data.dialects.{name}", fromlist=["x"])
        assert mod.NAME_AR and mod.SAMPLE_GREETING


def test_iraqi_detection():
    assert iraq.detect("شلونك اليوم شخبارك")
    assert not iraq.detect("hello there")


if __name__ == "__main__":
    test_all_loaded()
    test_iraqi_detection()
    print("نجح: 28 لهجة")
