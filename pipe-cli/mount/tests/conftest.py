import os

from utils import KB, MB, GB, as_literal, as_size, execute

source_path = os.path.join('.', 'random.tmp')
local_path = '.'
mounted_path = os.path.join('.', 'mounted-bucket')

default_sizes = [1, 2, 10, 1000,
                 1 * KB, 1000 * KB, 1050 * KB,
                 1 * MB, 2 * MB, 4 * MB, 5 * MB, 8 * MB, 10 * MB, 50 * MB, 600 * MB,
                 1 * GB]


def pytest_addoption(parser):
    parser.addoption('--sizes', help='File sizes to perform tests for')


def pytest_generate_tests(metafunc):
    if 'local_file' in metafunc.fixturenames and 'mounted_file' in metafunc.fixturenames:
        sizes = _get_sizes(config = metafunc.config)
        size_literals = [as_literal(size) for size in sizes]
        file_names = ['file_' + size for size in size_literals]
        local_files = [os.path.join(local_path, file_name) for file_name in file_names]
        mounted_files = [os.path.join(mounted_path, file_name) for file_name in file_names]
        metafunc.parametrize('size, local_file, mounted_file, source_path',
                             zip(size_literals, local_files, mounted_files, [source_path] * len(sizes)),
                             ids=size_literals)


def _get_sizes(config):
    raw_sizes_string = config.option.sizes
    if raw_sizes_string:
        sizes = [as_size(raw_size.strip())
                 for raw_size in raw_sizes_string.split(',')
                 if raw_size.strip()]
    else:
        sizes = default_sizes
    return sorted(sizes)


def pytest_sessionstart(session):
    sizes = _get_sizes(config=session.config)
    execute('head -c %s %s > %s' % (sizes[-1], '/dev/urandom', source_path))


def pytest_sessionfinish(session, exitstatus):
    sizes = _get_sizes(config = session.config)
    size_literals = [as_literal(size) for size in sizes]
    file_names = ['file_' + size for size in size_literals]
    local_files = [os.path.join(local_path, file_name) for file_name in file_names]
    mounted_files = [os.path.join(mounted_path, file_name) for file_name in file_names]
    for path in local_files + mounted_files:
        if os.path.exists(path):
            os.remove(path)
    os.remove(source_path)
