from cpaibot.common.logger import CpLogger as Logger
from cpaibot.pipeline.preferences import get_preference_value


default_logger = Logger("cpaibot")


class Preferences:
    def __init__(self):
        self._application_name = None
        self._prefs = {}

    @property
    def deployment_name(self) -> str:
        if not self._application_name:
            pref_value = self.get_preference("ui.pipeline.deployment.name")
            self._application_name = pref_value or "Cloud Pipeline"
        return self._application_name

    def get_preference(self, name: str):
        if name in self._prefs:
            return self._prefs[name]
        try:
            pref_value = get_preference_value(name)
        except:
            pref_value = None
        self._prefs[name] = pref_value
        return pref_value


preferences = Preferences()
