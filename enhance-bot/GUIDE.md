# دليل التثبيت والضبط

## التثبيت
```bash
# متطلبات النظام (Ubuntu/Debian)
sudo apt update && sudo apt install -y ffmpeg libraqm0 libfribidi0 libglib2.0-0
# مكتبات بايثون
pip install aiogram python-dotenv aiohttp-socks uvloop pillow numpy opencv-python-headless arabic-reshaper python-bidi
# التشغيل
echo "BOT_TOKEN=xxxx" > .env && python bot.py
```

## كيف يعمل كل جزء وأين تعدّل القوة
| الملف | الفكرة | ما تعدّله |
|---|---|---|
| `autograde.py` → `analyze()` | يأخذ 8 لقطات ويقيس: متوسط السطوع، أدنى/أعلى إضاءة (p1/p99)، التشبع، ميل اللون (RGB)، التشويش (Immerkaer)، الحدة (تباين لابلاس). | عدد اللقطات `n` |
| `autograde.py` → `chain()` | يحوّل القياسات إلى فلاتر FFmpeg: `eq gamma` لتصحيح التعريض نحو 0.47، `colorlevels` لفرد المدى بلا قصّ، `colorchannelmixer` توازن أبيض بنصف القوة، `vibrance` يرفع الألوان الباهتة أكثر من الزاهية، منحنى S للتباين، `hqdn3d` للتشويش (فقط لو مرتفع)، `unsharp` أقوى للضبابي. | القاموس `MODES` (sat/contrast/sharp/hdr) — ارفع القيم لألوان أغنى أو حدة أعلى |
| `cvlib.py` | معالجات OpenCV لكل إطار: `hdr_fusion` (تعريضات اصطناعية + دمج Mertens + CLAHE)، `retinex` للظلام، `dehaze` للضباب، `detail` لتضخيم التفاصيل بفلتر ثنائي، `filmic` (ACES)، `super_res` (Lanczos + توضيح). | `strength`/`mix`/`clip` في كل دالة |
| `beats.py` | يكتشف BPM وطور الضربات من الصوت (تدفق طيفي + ارتباط ذاتي + صقل الدورة) ليتزامن الفلاش/الزوم/الاهتزاز مع الإيقاع. | نطاق BPM 60–180 |
| `effects.py` | كل تأثير = سلسلة فلاتر. «إيديت» تستخدم `mod(t-phase, period)` لتتكرر مع الضربات. | الأرقام داخل التعابير (مثل `0.13` في زوم النبض) |
| `compose.py` | يجمع المكدس في أمر FFmpeg واحد، ثم OpenCV كمرحلة أخيرة، ويضبط معدل البت ليبقى الملف < 50MB. | `crf` (أقل = جودة أعلى) |

## قواعد جودة مهمة
- لا قصّ للألوان: `colorlevels` يحدّ الأسود/الأبيض بنسبة 0.55 فقط من الفجوة، ومنحنى S خفيف.
- التنقية مشروطة بالتشويش المقاس (لا تنعيم زائد).
- الألوان بين المقاطع: «تلقائي ذكي» يطبّق نفس المنطق على كل فيديو فتتقارب النتائج.
