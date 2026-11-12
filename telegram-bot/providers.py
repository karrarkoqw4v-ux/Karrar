"""مزوّدو ذكاء جاهزون (نفس قائمة تطبيق بوصلة): اختر الاسم فقط، وعنوان URL يُضبط تلقائياً حسب نوعه."""

# name, base_url, default_model, alt_model
PROVIDERS = [
    ("Anthropic Claude", "https://api.anthropic.com/v1", "claude-sonnet-5-5", "claude-opus-5-5"),
    ("DeepSeek", "https://api.deepseek.com", "deepseek-chat", "deepseek-reasoner"),
    ("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "o3-mini"),
    ("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-flash-latest", "gemini-flash-latest"),
    ("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", "deepseek/deepseek-r1:free"),
    ("GitHub Models", "https://models.github.ai/inference", "openai/gpt-4o-mini", "openai/o3-mini"),
    ("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", "magistral-small-latest"),
    ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "openai/gpt-oss-20b"),
]


def by_name(name: str):
    for p in PROVIDERS:
        if p[0] == name:
            return p
    return None
