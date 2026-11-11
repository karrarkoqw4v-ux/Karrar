# تشغيل النموذج (serve/)

بعد التدريب، نموذجك (`out/chat.pt`) يُشغَّل بطريقتين:

## 1) خادم API (`api_server.py`)
```bash
python serve/api_server.py --ckpt out/chat.pt --port 8000 --key SECRET
```
يقدّم واجهة متوافقة مع OpenAI (`/v1/models`، `/v1/chat/completions`، بث وبدونه). **اختُبر فعلياً** هنا:
مفتاح الوصول (401 بلا مفتاح صحيح)، قائمة النماذج، رد عادي، وبث حي — كلها عملت على خادم تجريبي صغير.
اربطه بتطبيق بوصلة عبر الإعدادات، مزوّد "Bawsala LM (my server)".

## 2) محادثة طرفية (`chat_cli.py`)
```bash
python serve/chat_cli.py --ckpt out/chat.pt
```
للتجربة السريعة بدون فتح منفذ شبكة.

كلا الملفين يعتمدان على `model_runtime.py` (تحميل النموذج وبناء الحوار) و`openai_compat.py` (صيغ الاستجابة)،
ليبقى منطق التشغيل في مكان واحد ولا يتكرر.
