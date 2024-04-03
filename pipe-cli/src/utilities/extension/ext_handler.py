import re


class ExtensionApplicationRule:
    REGEXP = 1
    EQUALS = 2

    def __init__(self, key, value, rule_type):
        self.key = key
        self.rule_type = rule_type
        self.value = value

    def test(self, key, value):
        if self.rule_type == ExtensionApplicationRule.EQUALS:
            return self.key == key and value == self.value
        elif self.rule_type == ExtensionApplicationRule.REGEXP:
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
                if rule.test(arg, value):
                    return True
        return False
