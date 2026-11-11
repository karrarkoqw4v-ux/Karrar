"""يجمع كل مهمات data/wiki في قائمة واحدة يستهلكها train/06_data_missions.py."""
from . import arwiki, arwikibooks, arwikisource, arwiktionary


def all_missions() -> list:
    return [arwiki.mission(), arwikibooks.mission(), arwikisource.mission(), arwiktionary.mission()]
