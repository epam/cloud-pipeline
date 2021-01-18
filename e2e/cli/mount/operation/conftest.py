import logging
import os
from datetime import datetime

import pytest
from py.xml import html

from ..utils import as_literal, execute, mkdir, KB, MB, MiB
from pyfs import rm


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    funtion_docs = (item.function.__doc__ or '').strip().splitlines()
    docs_test_case = funtion_docs[0].strip() if funtion_docs else ''
    arguments_test_case = item.funcargs.get('test_case')
    report.test_case = docs_test_case \
                       or arguments_test_case \
                       or ''
    if report.failed:
        logging.error('%s: %s FAILED:\n %s', report.test_case, report.nodeid, report.longrepr)
    report.longrepr = None


def pytest_html_results_table_header(cells):
    cells.insert(0, html.th('Test Case'))


def pytest_html_results_table_row(report, cells):
    cells.insert(0, html.td(str(report.test_case)))


def pytest_addoption(parser):
    parser.addoption('--webdav', help='Executes tests against a webdav data storage.', action='store_true')
    parser.addoption('--object', help='Executes tests against an object data storage.', action='store_true')
    parser.addoption('--prefix', help='Executes tests against an object data storage with prefix.', action='store_true')
    parser.addoption('--small', help='Executes tests for only small subset of file sizes.', action='store_true')
    parser.addoption('--logs-path', help='Specifies test logs directory path.')
    parser.addoption('--logs-level', help='Specifies test logs level where applicable.')


@pytest.fixture(scope='module', autouse=True)
def cleanup_before_module(request):
    rm(request.node.config.local_path, under=True, recursive=True, force=True)
    rm(request.node.config.mount_path, under=True, recursive=True, force=True)


@pytest.fixture(scope='function')
def check_mount_after_function(request):
    execute("""
        if ! mount | grep '{root_mount_path}' > /dev/null
        then
            echo "Mount at {root_mount_path} is not accessible"
            exit 1
        fi
    """.format(root_mount_path=request.node.config.root_mount_path))


@pytest.fixture(scope='function')
def mount_path(request):
    return request.node.config.mount_path


@pytest.fixture(scope='function')
def local_path(request):
    return request.node.config.local_path


@pytest.fixture(scope='function')
def source_path(request):
    return request.node.config.source_path


@pytest.fixture(scope='function')
def chunk_size(request):
    return request.node.config.chunk_size


def pytest_generate_tests(metafunc):
    sizes = metafunc.config.sizes.get(metafunc.module.__name__, [])
    if all(fixture in metafunc.fixturenames for fixture in ['size', 'local_file', 'mount_file']):
        metafunc.parametrize('size,local_file,mount_file',
                             zip(sizes,
                                 map(lambda size: os.path.join(metafunc.config.local_path, as_literal(size)), sizes),
                                 map(lambda size: os.path.join(metafunc.config.mount_path, as_literal(size)), sizes)),
                             ids=map(as_literal, sizes))
    elif all(fixture in metafunc.fixturenames for fixture in ['local_file', 'mount_file']):
        metafunc.parametrize('local_file,mount_file',
                             zip(map(lambda size: os.path.join(metafunc.config.local_path, as_literal(size)), sizes),
                                 map(lambda size: os.path.join(metafunc.config.mount_path, as_literal(size)), sizes)),
                             ids=map(as_literal, sizes))


def pytest_sessionstart(session):
    try:
        workdir_path = os.getcwd()
        default_local_path = os.path.join(workdir_path, 'e2e-local')
        default_root_mount_path = os.path.join(workdir_path, 'e2e-mount')
        default_logs_path = os.path.join(workdir_path, 'e2e-logs')
        default_source_path = os.path.join(workdir_path, 'e2e-random')
        default_logs_level = 'ERROR'
        default_chunk_size = 10 * MB
        default_buffer_size = 512 * MB
        default_read_ahead_size = 20 * MB
        default_small_sizes = {
            'cli.mount.operation.test_fallocate': [1],
            'cli.mount.operation.test_truncate': [1],
            'cli.mount.operation.test_read': [1],
            'cli.mount.operation.test_write': [1]
        }
        default_sizes = {
            'cli.mount.operation.test_fallocate': [1,
                                                   default_chunk_size + 1 * MB],
            'cli.mount.operation.test_truncate': [0, 1,
                                                  default_chunk_size + 1 * MB],
            'cli.mount.operation.test_read': [0, 1,
                                              default_chunk_size + 1 * MB,
                                              default_read_ahead_size * 2 + 1 * MB],
            'cli.mount.operation.test_write': [0, 1, 1 * KB, 1 * MB, 1 * MiB,
                                               default_chunk_size,
                                               default_chunk_size * 4 + 1 * MB,
                                               default_buffer_size,
                                               default_buffer_size + 1 * MB]
        }

        session.config.local_path = default_local_path
        session.config.root_mount_path = default_root_mount_path
        session.config.logs_path = session.config.option.logs_path or default_logs_path
        session.config.logs_level = session.config.option.logs_level or default_logs_level
        session.config.source_path = default_source_path
        session.config.chunk_size = default_chunk_size
        session.config.sizes = default_sizes if not session.config.option.small else default_small_sizes
        session.config.source_size = max(map(max, session.config.sizes.values()))

        logging.basicConfig(filename=os.path.join(session.config.logs_path, 'tests.log'), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s: %(message)s')

        api = os.environ['API']
        api = api if not api.endswith('/') else api[:-1]
        api_token = os.environ['API_TOKEN']
        storage_region = os.environ['CP_TEST_REGION_ID']
        storage_folder = os.getenv('CP_TEST_FOLDER_ID')
        storage_provider = os.environ['CP_PROVIDER']
        storage_type = _get_storage_type(config=session.config, storage_provider=storage_provider)
        session.config.storage_name = _generate_storage_name(storage_type=storage_type, storage_region=storage_region)
        session.config.storage_path = session.config.storage_name

        mkdir(session.config.local_path, session.config.root_mount_path, session.config.logs_path)

        if storage_type in ['S3', 'AZ', 'GS']:
            if session.config.option.prefix:
                session.config.storage_path += '/prefix'
            session.config.mount_path = session.config.root_mount_path
            execute("""
            head -c '{source_size}' /dev/urandom > '{source_path}'
            
            pipe storage create -c \
                                -n '{storage_name}' \
                                -p '{storage_path}' \
                                -d '' \
                                -sts '' \
                                -lts '' \
                                -b '' \
                                -t '{storage_type}' \
                                -r '{region}' \
                                -u '' \
                                -f '{folder}'
            
            pipe storage mount -t -l '{logs_path}/mount.log' -v '{logs_level}' -b '{storage_path}' '{root_mount_path}'
            
            counter=0
            while ! mount | grep '{root_mount_path}' > /dev/null && [ "$counter" -lt "10" ]; do
                counter=$((counter+1))
                sleep 1
            done
            if ! mount | grep '{root_mount_path}' > /dev/null
            then
                echo "Mount at {root_mount_path} is not accessible"
                exit 1
            fi
            """.format(storage_name=session.config.storage_name,
                       storage_path=session.config.storage_path,
                       storage_type=storage_type,
                       region=storage_region,
                       folder=storage_folder or '',
                       logs_path=session.config.logs_path,
                       logs_level=session.config.logs_level,
                       source_size=session.config.source_size,
                       source_path=session.config.source_path,
                       local_path=local_path,
                       mount_path=session.config.mount_path,
                       root_mount_path=session.config.root_mount_path))
        else:
            storage_share_id = os.environ['CP_TEST_SHARE_ID']
            storage_share_root = os.environ['CP_TEST_SHARE_ROOT']
            session.config.mount_path = os.path.join(session.config.root_mount_path,
                                                     session.config.storage_name.replace('-', '_'))

            execute("""
            head -c '{source_size}' /dev/urandom > '{source_path}'
            
            curl -k -X POST \
                 --header 'Content-Type: application/json' \
                 --header 'Accept: application/json' \
                 --header 'Authorization: Bearer {api_token}' \
                 -d '{{ \
                     "fileShareMountId": "{share_id}", \
                     "name": "{storage_name}", \
                     "path": "{share_root}:/{storage_path}", \
                     "parentFolderId": "{folder}", \
                     "regionId": "{region}", \
                     "serviceType": "FILE_SHARE" \
                  }}' \
                  '{api}/datastorage/save?cloud=true&skipPolicy=false' \
                  > /dev/null 2>&1
            
            pipe storage mount -t -l '{logs_path}/mount.log' -v '{logs_level}' -f '{root_mount_path}'
            
            counter=0
            while ! mount | grep '{root_mount_path}' > /dev/null && [ "$counter" -lt "10" ]; do
                counter=$((counter+1))
                sleep 1
            done
            if ! mount | grep '{root_mount_path}' > /dev/null
            then
                echo "Mount at {root_mount_path} is not accessible"
                exit 1
            fi
            
            counter=0
            while [ ! -d "{mount_path}" ] && [ "$counter" -lt "70" ]; do
                counter=$((counter+1))
                sleep 1
            done
            if [ ! -d "{mount_path}" ]
            then
                echo "Mount directory at {mount_path} is not accessible"
                exit 1
            fi
            """.format(api=api,
                       api_token=api_token,
                       storage_name=session.config.storage_name,
                       storage_path=session.config.storage_path,
                       storage_type=storage_type,
                       region=storage_region,
                       share_id=storage_share_id,
                       share_root=storage_share_root,
                       folder=storage_folder or '',
                       logs_path=session.config.logs_path,
                       logs_level=session.config.logs_level,
                       source_size=session.config.source_size,
                       source_path=session.config.source_path,
                       local_path=session.config.local_path,
                       mount_path=session.config.mount_path,
                       root_mount_path=session.config.root_mount_path))
    except Exception:
        import sys
        logging.error('Session start has failed', exc_info=sys.exc_info())
        raise


def pytest_sessionfinish(session, exitstatus):
    try:
        execute("""
        EXIT_CODE=0
        pipe storage umount '{root_mount_path}' || EXIT_CODE=$?
        pipe storage delete -y -c -n '{storage_name}' || EXIT_CODE=$?
        if [ -f "{source_path}" ]; then rm -f "{source_path}"; fi
        if [ -d "{local_path}" ]; then rm -rf "{local_path}"; fi
        if ! mount | grep '{root_mount_path}' > /dev/null && [ -d "{root_mount_path}" ]; then rm -rf "{root_mount_path}"; fi
        exit $EXIT_CODE
        """.format(storage_name=session.config.storage_name,
                   source_path=session.config.source_path,
                   local_path=session.config.local_path,
                   mount_path=session.config.mount_path,
                   root_mount_path=session.config.root_mount_path))
    except Exception:
        import sys
        logging.error('Session finish has failed', exc_info=sys.exc_info())
        raise


def _get_storage_type(config, storage_provider):
    if config.option.webdav:
        storage_kind = 'webdav'
    elif config.option.object:
        storage_kind = 'object'
    else:
        storage_kind = 'object'
    storage_type_dict = {
        'S3': {
            'webdav': 'NFS',
            'object': 'S3'
        },
        'GS': {
            'object': 'GS'
        }
    }
    return storage_type_dict.get(storage_provider, {}).get(storage_kind) or 'S3'


def _generate_storage_name(storage_type, storage_region):
    return 'e2e-mount-{type}-{region}-{time}' \
        .format(type=storage_type, region=storage_region, time=datetime.now().strftime('%d-%m-%Y-%H-%M-%S')) \
        .lower()
