import re


class SynchronizerConfig:

    def __init__(self, execution_max_running_days=2, mode="single", at_time="00:01"):
        self.execution_max_running_days = execution_max_running_days
        self.mode = mode
        self.at_time = None
        if mode == "daemon":
            self.at_time = at_time

    def to_json(self):
        return {
            "execution_max_running_days": self.execution_max_running_days,
            "mode": self.mode,
            "at_time": self.at_time
        }
