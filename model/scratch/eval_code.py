"""Learn-by-testing loop for code. For each task in evals/tasks.jsonl: ask YOUR served model (serve.py) for code, RUN it with the task's unit test
(python / node, 10 s timeout, temp dir), report pass rate, and save every PASSING solution to data/sft/self_learned.jsonl so the next sft.py round
trains on code that really worked (rejection sampling). Failing code is never saved.
    python eval_code.py --url http://127.0.0.1:8000/v1 --samples 4
Run only code from your own model on your own machine; for an untrusted model use a container (see docker-compose.yml)."""
import argparse, json, os, re, shutil, subprocess, sys, tempfile, urllib.request

RUN = {"python": ("py", [sys.executable, "-I"]), "javascript": ("js", ["node"])}

def ask(url, key, prompt, temp):
    req = urllib.request.Request(url.rstrip("/") + "/chat/completions", json.dumps({"messages": [{"role": "user", "content": prompt}], "max_tokens": 300, "temperature": temp}).encode(),
                                 {"Content-Type": "application/json", "Authorization": "Bearer " + key})
    return json.load(urllib.request.urlopen(req, timeout=300))["choices"][0]["message"]["content"]

def extract(txt):
    m = re.search(r"```[a-zA-Z+#]*\n(.*?)```", txt, re.S)
    return (m.group(1) if m else txt).strip()

def passes(lang, code, test):
    if lang not in RUN or (lang == "javascript" and not shutil.which("node")): return None
    ext, cmd = RUN[lang]
    with tempfile.TemporaryDirectory() as d:
        f = os.path.join(d, "t." + ext); open(f, "w", encoding="utf-8").write(code + "\n" + test + "\n")
        try:
            r = subprocess.run(cmd + [f], cwd=d, capture_output=True, timeout=10, env={"PATH": os.environ.get("PATH", "")})
            return r.returncode == 0
        except subprocess.TimeoutExpired:
            return False

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:8000/v1"); ap.add_argument("--key", default="x"); ap.add_argument("--tasks", default="evals/tasks.jsonl")
    ap.add_argument("--samples", type=int, default=4); ap.add_argument("--temperature", type=float, default=0.8); ap.add_argument("--save", default="data/sft/self_learned.jsonl")
    a = ap.parse_args(); os.makedirs(os.path.dirname(a.save), exist_ok=True)
    tot = ok = 0; res = {}
    out = open(a.save, "a", encoding="utf-8")
    for line in open(a.tasks, encoding="utf-8"):
        t = json.loads(line); solved = False
        for _ in range(a.samples):
            ans = ask(a.url, a.key, t["prompt"], a.temperature); r = passes(t["lang"], extract(ans), t["test"])
            if r is None: break
            tot += 1; ok += r
            if r:
                solved = True
                out.write(json.dumps({"messages": [{"role": "user", "content": t["prompt"]}, {"role": "assistant", "content": ans}]}, ensure_ascii=False) + "\n"); break
        res[t["id"]] = solved; print(("PASS " if solved else "FAIL ") + t["id"])
    print(f"tasks solved: {sum(res.values())}/{len(res)}   (attempt pass rate {ok}/{tot})")
    json.dump({"solved": res}, open("out/eval_last.json", "w") if os.path.isdir("out") else sys.stdout)

if __name__ == "__main__":
    main()
