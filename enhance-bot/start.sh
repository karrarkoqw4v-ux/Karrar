#!/bin/bash
# تشغيل سريع على أي خادم لينكس
cd "$(dirname "$0")"
python3 -m pip install -r requirements.txt
python3 main.py
