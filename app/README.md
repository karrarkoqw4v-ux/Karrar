# Karrar AI — Android chat app (dark, DeepSeek-style layout)

Pure-Java Android app (no external libraries). Features: dark/light theme, Arabic (RTL) / English,
side drawer with chat history, DeepThink (visible reasoning), web search with sources, Markdown + code blocks,
streaming replies, any OpenAI-compatible API (DeepSeek, OpenAI, OpenRouter, Groq, Ollama), large settings page.
Without an API key the app runs in demo mode.

## Build
Needs `aapt`, `zipalign`, `apksigner` (apt), a JDK, and in `tools/jars/`: `android-all.jar`
(org.robolectric:android-all:14-robolectric-10818077) and `dx.jar` (com.jakewharton.android.repackaged:dalvik-dx:14.0.0_r21).

    ./tools/build.sh   # -> Karrar-AI.apk
