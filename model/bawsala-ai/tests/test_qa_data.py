"""يتأكد أن أسئلة وأجوبة التأسيس (12 موضوعاً) محمَّلة وغير فارغة."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from data import qa


def test_12_topics():
    assert len(qa.TOPICS) == 12, f"المتوقع 12 موضوعاً، الموجود {len(qa.TOPICS)}"
    for topic, pairs in qa.TOPICS.items():
        assert pairs, f"الموضوع {topic} فارغ"
        for q, a in pairs:
            assert q and a


if __name__ == "__main__":
    test_12_topics()
    print("نجح: 12 موضوع أسئلة، إجمالي", sum(len(v) for v in qa.TOPICS.values()), "سؤالاً")
