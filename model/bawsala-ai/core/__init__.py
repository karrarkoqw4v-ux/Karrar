"""بنية نموذج بوصلة: الشبكة العصبية فقط (بدون بيانات أو تدريب)."""
from .config import PRESETS, ModelConfig
from .model import BawsalaGPT

__all__ = ["ModelConfig", "PRESETS", "BawsalaGPT"]
