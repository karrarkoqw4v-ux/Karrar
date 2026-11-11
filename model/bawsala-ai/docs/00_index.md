# فهرس بيئة بوصلة AI

| الملف | ماذا يفعل بسطر واحد |
|---|---|
| `core/config.py` | أحجام النموذج المتاحة (tiny/small/b1) |
| `core/norm.py` | تطبيع الطبقات (RMSNorm) |
| `core/rope.py` | ترميز المواضع الدوّار (RoPE) |
| `core/attention.py` | طبقة الانتباه الذاتي السببية |
| `core/mlp.py` | طبقة SwiGLU |
| `core/block.py` | طبقة Transformer واحدة كاملة |
| `core/model.py` | النموذج الكامل: forward + generate |
| `data/wiki/*.py` | جمع ويكيبيديا وويكي كتب ومصادر وقاموس عربي (6 ملفات) |
| `data/code/<لغة>.py` | وصف مهمة جمع كود لكل من 26 لغة برمجة |
| `data/dialects/<لهجة>.py` | عيّنة ونمط لكل من 28 لهجة/لغة |
| `data/qa/<موضوع>.py` | أسئلة وأجوبة تأسيسية لكل من 12 موضوعاً |
| `data/web/fetch.py` | يجلب صفحة واحدة بأمان (https فقط، يحترم robots.txt) |
| `data/web/html_extract.py` | يستخرج النص القابل للقراءة من HTML |
| `data/web/robots.py` | فحص robots.txt |
| `data/web/state.py` | حفظ/استئناف تقدّم الجمع |
| `data/web/mission.py` | ينفّذ جمع روابط المستخدم |
| `skills/programming/<لغة>.py` | ينفّذ ويختبر كود كل من 26 لغة برمجة فعلياً حيث يتوفر مترجم |
| `skills/design/<نوع>.py` | قالب وقواعد توليد لكل من 12 نوع صفحة ويب |
| `skills/repo/<أداة>.py` | وصف أدوات إدارة المستودع الثمانية |
| `train/01_tokenizer.py` | تدريب توكنايزر BPE |
| `train/02_prep_data.py` | تنظيف وتوكنة وكتابة ملفات bin |
| `train/03_pretrain.py` | التدريب الأولي (قابل للاستئناف) |
| `train/04_build_sft.py` | بناء بيانات المحادثة من qa+dialects+ملفاتك |
| `train/05_sft.py` | تعليم المحادثة (الضبط) |
| `train/06_data_missions.py` | تنفيذ كل مهمات جمع البيانات بالترتيب |
| `train/07_eval_code.py` | تعلّم بالاختبار (كود فعلي يُنفَّذ ويُحكم عليه) |
| `train/08_eval_chat.py` | تقييم تقريبي لجودة الرد العام |
| `train/09_cli_generate.py` | توليد نص من نقطة تفتيش مباشرة |
| `train/10_pipeline.py` | يشغّل كل المراحل بأمر واحد (للتجربة) |
| `serve/model_runtime.py` | تحميل النموذج وبناء/توليد الحوار |
| `serve/openai_compat.py` | صيغ استجابة متوافقة مع OpenAI |
| `serve/auth.py` | تحقق من مفتاح الخادم |
| `serve/api_server.py` | خادم HTTP متوافق مع OpenAI |
| `serve/chat_cli.py` | محادثة في الطرفية مباشرة |
| `tests/*.py` | اختبارات حقيقية تُشغَّل فعلياً (10 ملفات) + `run_all.py` |
| `config/*.py` | مسارات وسياسة وتبعيات مركزية |

راجع `docs/01_architecture.md` فما بعده لشرح كل جزء بالتفصيل.
