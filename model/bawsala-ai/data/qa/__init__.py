"""أسئلة وأجوبة تأسيسية مصنّفة حسب الموضوع (12 ملفاً)، تُستخدم كبذرة لمرحلة الضبط (SFT)."""
TOPICS = {}
from . import identity
TOPICS["identity"] = identity.PAIRS
from . import geography
TOPICS["geography"] = geography.PAIRS
from . import math
TOPICS["math"] = math.PAIRS
from . import science
TOPICS["science"] = science.PAIRS
from . import programming_python
TOPICS["programming_python"] = programming_python.PAIRS
from . import programming_js
TOPICS["programming_js"] = programming_js.PAIRS
from . import programming_web
TOPICS["programming_web"] = programming_web.PAIRS
from . import capabilities
TOPICS["capabilities"] = capabilities.PAIRS
from . import honesty
TOPICS["honesty"] = honesty.PAIRS
from . import courtesy
TOPICS["courtesy"] = courtesy.PAIRS
from . import religion_culture
TOPICS["religion_culture"] = religion_culture.PAIRS
from . import time_calendar
TOPICS["time_calendar"] = time_calendar.PAIRS
