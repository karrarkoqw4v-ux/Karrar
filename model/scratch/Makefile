# بيئة بوصلة LM: كل شيء بأمر واحد لكل مرحلة.   make help
PY ?= python3
help:
	@echo "make setup      - install dependencies"
	@echo "make data       - collect Arabic text + code in 25 languages (licensed sets, resumable)"
	@echo "make tokenizer  - train the tokenizer on what was collected"
	@echo "make prep       - tokenize text, then append code (whole documents)"
	@echo "make train      - pre-train (resumable; Ctrl+C safe)"
	@echo "make chat-data  - build chat data (seed + yours + self-learned passing code)"
	@echo "make sft        - teach it to chat"
	@echo "make serve      - serve your model as an OpenAI-compatible API on :8000"
	@echo "make learn      - code learn-by-testing round (needs make serve running)"
	@echo "make cycle      - chat-data + sft (run after make learn to train on code that really passed)"
setup:
	$(PY) -m pip install -r requirements.txt tokenizers datasets
data:
	$(PY) missions/runner.py --config missions/missions.json
	$(PY) missions/runner.py --config missions/code_languages.json --state missions/state_code.json
tokenizer:
	mkdir -p data/raw_all && cp -r data/raw/* data/raw_all/ 2>/dev/null; cp -r data/code/* data/raw_all/ 2>/dev/null; true
	$(PY) tokenizer_train.py --input data/raw_all --vocab 32000
prep:
	$(PY) prep_data.py --input data/raw --target-tokens 600000000 --min-arabic 0.5
	$(PY) prep_data.py --input data/code --code --append --target-tokens 600000000 --out data/bin
train:
	$(PY) train.py --preset small --max-steps 20000 --batch 4 --grad-accum 32 --lr 6e-4 --resume
chat-data:
	$(PY) build_sft.py
	@test -f data/sft/self_learned.jsonl && cat data/sft/self_learned.jsonl >> data/sft/chat.jsonl || true
sft:
	$(PY) sft.py --epochs 3 --batch 8
serve:
	$(PY) serve.py --ckpt out/chat.pt --port 8000
learn:
	$(PY) eval_code.py --url http://127.0.0.1:8000/v1 --samples 4
cycle: chat-data sft
