"""يتأكد أن أدوات المستودع الثمانية موجودة ومعرَّفة، وأن أداة الأمان (safety_gate) موجودة تحديداً."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from skills import repo
from skills.repo import safety_gate


def test_8_tools():
    names = [n for n in dir(repo) if hasattr(getattr(repo, n), "TOOL_ID")]
    assert len(names) == 8, f"المتوقع 8 أدوات، الموجود {len(names)}"
    assert safety_gate.TOOL_ID == "safety_gate"


if __name__ == "__main__":
    test_8_tools()
    print("نجح: 8 أدوات مستودع")
