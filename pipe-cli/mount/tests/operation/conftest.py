import os

from utils import KB, MB, GB, as_literal, as_size, execute

root_path = os.getcwd()
source_path = os.path.join(root_path, 'random.tmp')
default_local_path = os.path.join(root_path, 'local')
default_mount_path = os.path.join(root_path, 'mount')

default_small_sizes = [1 * MB, 2 * MB]
default_big_sizes = [1, 2, 10, 1000,
                     1 * KB, 1000 * KB, 1050 * KB,
                     1 * MB, 2 * MB, 4 * MB, 5 * MB, 8 * MB, 10 * MB, 20 * MB, 50 * MB, 600 * MB,
                     1 * GB]
default_sizes = default_small_sizes
containers = {}


def pytest_addoption(parser):
    parser.addoption('--sizes', help='File sizes to perform tests for.')
    parser.addoption('--local', help='Local path.')
    parser.addoption('--mount', help='Mount path.')
    parser.addoption('--small', help='Performs test scenarios using small sized files.', action='store_true')
    parser.addoption('--big', help='Performs test scenarios using big sized files.', action='store_true')


def pytest_generate_tests(metafunc):
    local_path = _get_local_path(config=metafunc.config)
    mounted_path = _get_mount_path(config=metafunc.config)
    # todo: We should run each test in module directory.
    #  Nevertheless tests should be performed for both mount root and folders.
    # module_local_path = os.path.join(local_path, metafunc.module.__name__)
    # module_mounted_path = os.path.join(mounted_path, metafunc.module.__name__)
    if any(operation in metafunc.module.__name__ for operation in ['read', 'write', 'truncate', 'fallocate']):
        sizes = _get_sizes(config=metafunc.config)
        size_literals = [as_literal(size) for size in sizes]
        file_names = ['file_' + size for size in size_literals]
        local_files = [os.path.join(local_path, file_name) for file_name in file_names]
        mounted_files = [os.path.join(mounted_path, file_name) for file_name in file_names]
        parameters = zip(size_literals, local_files, mounted_files, [source_path] * len(sizes))
        metafunc.parametrize('size, local_file, mounted_file, source_path', parameters, ids=size_literals)
    elif any(operation in metafunc.module.__name__ for operation in ['mkdir', 'mv', 'rm', 'touch']):
        metafunc.parametrize('mount_path', [mounted_path], ids=[''])


def _get_local_path(config):
    return config.option.local or default_local_path


def _get_mount_path(config):
    return config.option.mount or default_mount_path


def _get_sizes(config):
    if config.option.small:
        return default_small_sizes
    if config.option.big:
        return default_big_sizes
    raw_sizes_string = config.option.sizes
    if raw_sizes_string:
        sizes = [as_size(raw_size.strip())
                 for raw_size in raw_sizes_string.split(',')
                 if raw_size.strip()]
    else:
        sizes = default_sizes
    return sorted(sizes)


def pytest_sessionstart(session):
    local_path = _get_local_path(config=session.config)
    mounted_path = _get_mount_path(config=session.config)
    sizes = _get_sizes(config=session.config)
    execute('head -c %s %s > %s' % (sizes[-1], '/dev/urandom', source_path))
    execute('mkdir -p %s %s' % (local_path, mounted_path))


def pytest_sessionfinish(session, exitstatus):
    local_path = _get_local_path(config=session.config)
    mounted_path = _get_mount_path(config=session.config)
    sizes = _get_sizes(config = session.config)
    size_literals = [as_literal(size) for size in sizes]
    file_names = ['file_' + size for size in size_literals]
    local_files = [os.path.join(local_path, file_name) for file_name in file_names]
    mounted_files = [os.path.join(mounted_path, file_name) for file_name in file_names]
    local_file_tmps = [local_file + '.copy' for local_file in local_files]
    mounted_file_tmps = [mounted_file + '.copy' for mounted_file in mounted_files]
    for path in local_files + mounted_files + local_file_tmps + mounted_file_tmps:
        if os.path.exists(path):
            os.remove(path)
    os.remove(source_path)
