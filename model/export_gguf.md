# دمج الأوزان وتصديرها

```bash
# 1) دمج محوّل LoRA مع النموذج الأساسي
python - <<'PY'
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel
base="Qwen/Qwen2.5-7B-Instruct"
m=AutoModelForCausalLM.from_pretrained(base, torch_dtype="auto")
m=PeftModel.from_pretrained(m,"out/bawsala-lora").merge_and_unload()
m.save_pretrained("out/bawsala-merged"); AutoTokenizer.from_pretrained(base).save_pretrained("out/bawsala-merged")
PY
# 2) تحويل إلى GGUF (llama.cpp) للتشغيل المحلي/الجوال
git clone https://github.com/ggerganov/llama.cpp && cd llama.cpp
python convert_hf_to_gguf.py ../out/bawsala-merged --outfile ../out/bawsala.gguf --outtype q8_0
```
ثم `ollama create bawsala -f Modelfile` للتشغيل المحلي.
