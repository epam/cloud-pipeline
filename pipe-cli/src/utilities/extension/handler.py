import re
from enum import Enum

from src.utilities.extension.omics import OmicsCopyFileHandler


class ExtensionApplicationRuleType(Enum):
    REGEXP = 1
    EQUALS = 2


class ExtensionApplicationRule:

    def __init__(self, key, value, rule_type: ExtensionApplicationRuleType):
        self.key = key
        self.rule_type = rule_type
        self.value = value

    def test(self, key, value):
        match self.rule_type:
            case ExtensionApplicationRuleType.EQUALS:
                return self.key == key and value == self.value
            case ExtensionApplicationRuleType.REGEXP:
                return self.key == key and re.match(self.value, value)


class ExtensionHandler:

    def __init__(self, command_group, command, rules):
        self.command_group = command_group
        self.command = command
        self.rules = rules

    def accept(self, command_group, command, arguments):
        if self._test(command_group, command, arguments):
            self._apply(arguments)
            return True
        return False

    def _apply(self, arguments):
        pass

    def _test(self, command_group, command, arguments):
        return command_group == self.command_group and command == self.command and self._test_arguments(arguments)

    def _test_arguments(self, arguments):
        for arg, value in arguments.items():
            for rule in self.rules:
                if rule.check(arg, value):
                    return True
        return False


class ExtensionHandlerRegistry:

    HANDLERS = [
        OmicsCopyFileHandler()
    ]

    @classmethod
    def accept(cls, command_group, command, arguments):
        for handler in cls.HANDLERS:
            if handler.accept(command_group, command, arguments):
                return True
        return False

