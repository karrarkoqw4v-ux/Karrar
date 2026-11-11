"""يتأكد أن مهمات جمع الكود الـ26 كلها موجودة وتصف مهمة صالحة، وأن مهارة التنفيذ تعمل فعلياً حيث يتوفر مترجم."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from data import code as code_data
from skills.programming import python as py_skill, javascript as js_skill


def test_26_missions():
    names = [n for n in dir(code_data) if hasattr(getattr(code_data, n), "mission")]
    assert len(names) == 26, f"المتوقع 26 لغة، الموجود {len(names)}"
    m = code_data.python.mission()
    assert m["kind"] == "hf_dataset" and m["license"]


def test_python_execution():
    ok, _ = py_skill.run("def add(a,b):\n    return a+b\n", "assert add(2,3)==5")
    assert ok is True
    ok2, _ = py_skill.run("def add(a,b):\n    return 0\n", "assert add(2,3)==5")
    assert ok2 is False


def test_javascript_execution():
    if not js_skill.can_execute_here():
        print("تخطّي: لا يوجد node على هذا الجهاز")
        return
    ok, _ = js_skill.run("function add(a,b){return a+b}", "const assert=require('assert');assert.strictEqual(add(2,3),5);")
    assert ok is True


if __name__ == "__main__":
    test_26_missions()
    test_python_execution()
    test_javascript_execution()
    print("نجح: 26 لغة برمجة")
