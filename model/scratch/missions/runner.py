"""Learning-agent runner: executes "missions" (data sources) one after another, counts tokens toward a target (default 2 billion),
saves progress (resumable) and sends notifications (ntfy / Telegram / stdout) at milestones and on completion.
It only collects text from sources whose license you declared in missions.json - it does NOT scrape the web at random.
Usage:  python missions/runner.py --config missions/missions.json   (add --notify stdout to test)"""
import argparse, json, os, re, sys, time, urllib.request, urllib.parse, urllib.robotparser
from html.parser import HTMLParser

class Text(HTMLParser):
    def __init__(self):
        super().__init__(); self.out, self.skip = [], 0
    def handle_starttag(self, t, a):
        if t in ("script", "style", "nav", "footer", "header", "noscript"): self.skip += 1
    def handle_endtag(self, t):
        if t in ("script", "style", "nav", "footer", "header", "noscript") and self.skip: self.skip -= 1
    def handle_data(self, d):
        if not self.skip and d.strip(): self.out.append(d.strip())

def notify(cfg, title, msg, override=None):
    kind = override or cfg.get("type", "stdout")
    print(f"[notify:{kind}] {title} - {msg}")
    try:
        if kind == "ntfy":
            req = urllib.request.Request(cfg["server"].rstrip("/") + "/" + cfg["topic"], data=msg.encode("utf-8"), method="POST",
                                         headers={"Title": title.encode("utf-8").decode("latin-1", "ignore") or "Bawsala", "Tags": "brain"})
            urllib.request.urlopen(req, timeout=15)
        elif kind == "telegram":
            url = f"https://api.telegram.org/bot{os.environ['TELEGRAM_TOKEN']}/sendMessage"
            urllib.request.urlopen(urllib.request.Request(url, data=urllib.parse.urlencode({"chat_id": os.environ["TELEGRAM_CHAT"], "text": title + "\n" + msg}).encode()), timeout=15)
    except Exception as e:
        print("notify failed:", e)

class Counter:
    def __init__(self, tok_path):
        self.tok = None
        if tok_path and os.path.exists(tok_path):
            from tokenizers import Tokenizer
            self.tok = Tokenizer.from_file(tok_path)
    def count(self, text):
        return len(self.tok.encode(text).ids) if self.tok else max(1, int(len(text) / 3.2))  # rough estimate when no tokenizer yet

def docs_hf(m):
    from datasets import load_dataset
    kw = {"data_dir": m["data_dir"]} if m.get("data_dir") else {}
    ds = load_dataset(m["name"], m.get("config"), split=m.get("split", "train"), streaming=True, **kw)
    for row in ds:
        yield row[m.get("text_field", "text")]

def docs_local(m):
    for root, _, fs in os.walk(m["path"]):
        for f in sorted(fs):
            if f.endswith((".txt", ".md", ".py", ".js", ".ts", ".kt", ".dart", ".java", ".go", ".rs", ".json", ".jsonl")):
                with open(os.path.join(root, f), encoding="utf-8", errors="ignore") as fh:
                    yield fh.read()

def docs_web(m):
    ua = "BawsalaLearningBot/1.0 (respects robots.txt)"
    rps = {}
    for url in [u.strip() for u in open(m["urls_file"], encoding="utf-8") if u.strip() and not u.startswith("#")]:
        host = urllib.parse.urlparse(url).netloc
        if host not in rps:
            rp = urllib.robotparser.RobotFileParser(); rp.set_url(f"https://{host}/robots.txt")
            try: rp.read()
            except Exception: rp = None
            rps[host] = rp
        if rps[host] is not None and not rps[host].can_fetch(ua, url):
            print("blocked by robots.txt:", url); continue
        try:
            raw = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": ua}), timeout=20).read().decode("utf-8", "ignore")
            p = Text(); p.feed(raw); yield "\n".join(p.out)
        except Exception as e:
            print("fetch failed:", url, e)
        time.sleep(m.get("delay_sec", 3))

SOURCES = {"hf_dataset": docs_hf, "local_dir": docs_local, "web_urls": docs_web}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default="missions/missions.json")
    ap.add_argument("--notify", default=None, help="override notifier: stdout|ntfy|telegram")
    ap.add_argument("--state", default="missions/state.json")
    a = ap.parse_args()
    cfg = json.load(open(a.config, encoding="utf-8"))
    state = json.load(open(a.state)) if os.path.exists(a.state) else {"missions": {}, "total": 0, "sent": []}
    counter = Counter(cfg.get("tokenizer"))
    target = cfg["target_tokens"]
    notify(cfg["notify"], "بوصلة: بدأ التعلّم", f"الهدف {target:,} توكن", a.notify)
    for m in cfg["missions"]:
        st = state["missions"].setdefault(m["id"], {"tokens": 0, "docs": 0, "done": False})
        if st["done"]:
            continue
        if m.get("license", "").lower() not in cfg["allowed_licenses"]:
            print(f"SKIP {m['id']}: license '{m.get('license')}' is not in allowed_licenses"); st["done"] = True; continue
        out = os.path.join(cfg["out_dir"], m["id"]); os.makedirs(out, exist_ok=True)
        shard, buf, buf_n = len([f for f in os.listdir(out) if f.endswith(".txt")]), [], 0
        skip = st["docs"]
        sep = "\n<|doc|>\n" if m.get("code") else "\n\n"   # code documents must stay whole (prep_data.py --code)
        try:
          for i, doc in enumerate(SOURCES[m["kind"]](m)):
              if i < skip: continue
              doc = re.sub(r"\n{3,}", "\n\n", doc or "").strip()
              if len(doc) < 50: continue
              n = counter.count(doc)
              buf.append(doc); buf_n += len(doc); st["tokens"] += n; st["docs"] = i + 1; state["total"] += n
              if buf_n > 20_000_000:
                  open(os.path.join(out, f"part-{shard:05d}.txt"), "w", encoding="utf-8").write(sep.join(buf)); shard += 1; buf, buf_n = [], 0
                  json.dump(state, open(a.state, "w"))
              for ms in cfg["milestones"]:
                  if state["total"] >= ms * target and ms not in state["sent"]:
                      state["sent"].append(ms)
                      notify(cfg["notify"], "بوصلة: تقدّم التعلّم", f"وصلتُ {int(ms*100)}% ({state['total']:,} توكن)", a.notify)
              if st["tokens"] >= m.get("max_tokens", target) or state["total"] >= target:
                  break
        except Exception as e:
            st["error"] = str(e)[:300]; print(f"FAILED {m['id']}: {e}")
            notify(cfg["notify"], "بوصلة: تعذّرت مهمة", f"{m['id']}: {str(e)[:120]}", a.notify)
        if buf:
            open(os.path.join(out, f"part-{shard:05d}.txt"), "w", encoding="utf-8").write(sep.join(buf))
        st["done"] = True
        json.dump(state, open(a.state, "w"))
        notify(cfg["notify"], "بوصلة: انتهت مهمة", f"{m['id']}: {st['tokens']:,} توكن", a.notify)
        if state["total"] >= target:
            break
    done = state["total"] >= target
    notify(cfg["notify"], "بوصلة: " + ("اكتمل الجمع 🎉" if done else "انتهت المهمات"), f"المجموع {state['total']:,} توكن. الخطوة التالية: prep_data.py ثم train.py", a.notify)

if __name__ == "__main__":
    main()
