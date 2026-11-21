#!/bin/bash
# تشغيل سريع على أي خادم لينكس
cd "$(dirname "$0")"
python3 -m pip install -r requirements.txt
exec python3 run_forever.py
