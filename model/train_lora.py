"""QLoRA fine-tuning of an open base model on chat data (jsonl with a "messages" list).
Usage: python train_lora.py --base Qwen/Qwen2.5-7B-Instruct --data data/iraqi_seed.jsonl --out out/bawsala-lora
Needs a CUDA GPU (a free Kaggle/Colab T4 can do a 7B model in 4-bit). Not run in the authoring sandbox."""
import argparse, json, torch
from datasets import load_dataset
from peft import LoraConfig
from transformers import AutoTokenizer, AutoModelForCausalLM, BitsAndBytesConfig
from trl import SFTTrainer, SFTConfig

SYSTEM = ("أنت «بوصلة»، مساعد ذكي ودود ومحترم. تجيب بلهجة المستخدم (العراقية إن تكلّم عراقي)، "
          "وتكتب كوداً صحيحاً كاملاً، وتشرح بوضوح، ولا تشتم أبداً.")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="Qwen/Qwen2.5-7B-Instruct")
    ap.add_argument("--data", default="data/iraqi_seed.jsonl")
    ap.add_argument("--out", default="out/bawsala-lora")
    ap.add_argument("--epochs", type=float, default=3)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--maxlen", type=int, default=2048)
    a = ap.parse_args()

    tok = AutoTokenizer.from_pretrained(a.base)
    if tok.pad_token is None:
        tok.pad_token = tok.eos_token
    bnb = BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4",
                             bnb_4bit_compute_dtype=torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16)
    model = AutoModelForCausalLM.from_pretrained(a.base, quantization_config=bnb, device_map="auto")

    ds = load_dataset("json", data_files=a.data, split="train")

    def fmt(ex):
        msgs = ex["messages"]
        if not msgs or msgs[0]["role"] != "system":
            msgs = [{"role": "system", "content": SYSTEM}] + msgs
        return {"text": tok.apply_chat_template(msgs, tokenize=False)}
    ds = ds.map(fmt, remove_columns=ds.column_names)

    lora = LoraConfig(r=32, lora_alpha=64, lora_dropout=0.05, task_type="CAUSAL_LM",
                      target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"])
    cfg = SFTConfig(output_dir=a.out, num_train_epochs=a.epochs, learning_rate=a.lr, per_device_train_batch_size=2,
                    gradient_accumulation_steps=8, lr_scheduler_type="cosine", warmup_ratio=0.03, logging_steps=10,
                    save_strategy="epoch", bf16=torch.cuda.is_bf16_supported(), fp16=not torch.cuda.is_bf16_supported(),
                    max_seq_length=a.maxlen, dataset_text_field="text", report_to="none")
    SFTTrainer(model=model, args=cfg, train_dataset=ds, peft_config=lora, processing_class=tok).train()
    model.save_pretrained(a.out)
    tok.save_pretrained(a.out)
    print("saved LoRA adapter to", a.out)

if __name__ == "__main__":
    main()
