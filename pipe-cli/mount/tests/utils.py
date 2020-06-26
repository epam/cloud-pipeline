import subprocess

KB = 1024
MB = KB * KB
GB = MB * KB


class CmdExecutor:

    def __init__(self):
        pass

    def execute(self, command):
        process = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
        out, err = process.communicate()
        exit_code = process.wait()
        if exit_code != 0:
            raise RuntimeError('Command \'%s\' execution has failed. '
                               'Out: %s. Err: %s.' % (command, out.rstrip(), err.rstrip()))
        return out

    def execute_to_lines(self, command):
        return self._non_empty(self.execute(command).splitlines())

    def _non_empty(self, elements):
        return [element for element in elements if element.strip()]


def execute(command):
    return CmdExecutor().execute(command)


def as_literal(size, units=None):
    if not units:
        units = ['', 'KB', 'MB', 'GB']
    return str(size) + units[0] \
        if size < 1024 or size / 1024 == 1 and size % 1024 != 0 \
        else as_literal(size >> 10, units[1:])


def as_size(literal):
    if literal.isdigit():
        return int(literal)
    units = {'KB': KB, 'MB': MB, 'GB': GB}
    for unit, unit_multiplier in units.items():
        if literal.endswith(unit):
            size_without_units = literal[:-len(unit)]
            if size_without_units.isdigit():
                return int(size_without_units) * unit_multiplier
    raise RuntimeError('Unsupported size %s is provided.' % literal)
