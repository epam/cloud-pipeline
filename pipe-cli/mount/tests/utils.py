import os
import random
import subprocess

from pytest import fail

KB = 1000
MB = KB * KB
GB = MB * KB


class CmdExecutor:

    def __init__(self):
        pass

    def execute(self, command, log_path=None):
        process = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE, env=os.environ)
        out, err = process.communicate()
        exit_code = process.wait()
        if log_path:
            with open(log_path, 'w') as log_file:
                log_file.write(out)
                log_file.write(err)
        if exit_code != 0:
            raise RuntimeError('Command \'%s\' execution has failed. '
                               'Out: %s. Err: %s.' % (command, out.rstrip(), err.rstrip()))
        return out

    def execute_to_lines(self, command):
        return self._non_empty(self.execute(command).splitlines())

    def _non_empty(self, elements):
        return [element for element in elements if element.strip()]


def execute(command, container=None, log_path=None):
    if container:
        return execute('docker exec %s bash -c "%s"' % (container, command), log_path=log_path)
    else:
        return CmdExecutor().execute(command, log_path=log_path).strip()


def as_literal(size, units=None):
    # todo: Size of 1024 incorrectly resolves to 1KB (which is 1000).
    if not units:
        units = ['', 'KB', 'MB', 'GB']
    return str(size) + units[0] \
        if size < KB or size / KB == 1 and size % KB != 0 \
        else as_literal(size / KB, units[1:])


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


def assert_content(local_file, mounted_file):
    local_sha = execute('shasum %s | awk \'{ print $1 }\'' % local_file)
    mounted_sha = execute('shasum %s | awk \'{ print $1 }\'' % mounted_file)
    if local_sha != mounted_sha:
        fail('Local and mounted file shas do not matches: %s %s' % (local_sha, mounted_sha))
