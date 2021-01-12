import os
from datetime import datetime

import pytest

from ..utils import as_literal, as_size, execute, mkdir, KB, MB

root_path = os.getcwd()
root_logs_path = os.path.join(root_path, 'e2e-logs')
tests_source_path = os.path.join(root_path, 'e2e-random')

default_storage_kind = 'object'
default_local_path = os.path.join(root_path, 'e2e-local')
default_mount_path = os.path.join(root_path, 'e2e-mount')
default_sizes = '0, 1, 1000 KB, 1 MB, 1050 KB, 30 MB, 600 MB'


def pytest_addoption(parser):
    parser.addoption('--webdav', help='Executes tests against a webdav data storage.', action='store_true')
    parser.addoption('--object', help='Executes tests against an object data storage.', action='store_true')
    parser.addoption('--local', help='Local testing directory path.')
    parser.addoption('--mount', help='Mount testing directory path.')
    parser.addoption('--sizes', help='File sizes to perform tests for.')


@pytest.fixture(scope='session')
def session_mount_path(request):
    session = request.node
    return session.config.storage_mount_path


@pytest.fixture(scope='function')
def mount_path(request):
    session = request.node
    return session.config.storage_mount_path


@pytest.fixture(scope='session')
def session_local_path(request):
    session = request.node
    return _get_local_path(config=session.config)


@pytest.fixture(scope='function')
def local_path(request):
    session = request.node
    return _get_local_path(config=session.config)


@pytest.fixture(scope='module', autouse=True)
def cleanup_before_module(session_mount_path, session_local_path):
    from pyfs import rm
    rm(session_mount_path, under=True, recursive=True, force=True)
    rm(session_local_path, under=True, recursive=True, force=True)


@pytest.fixture(scope='function', autouse=True)
def source_path():
    return tests_source_path


def pytest_generate_tests(metafunc):
    local_path = _get_local_path(config=metafunc.config)
    mount_path = metafunc.config.storage_mount_path

    # todo: We should run each test in its module directory.
    #  Nevertheless tests should be performed for both mount root and folders.
    # module_local_path = os.path.join(local_path, metafunc.module.__name__)
    # module_mount_path = os.path.join(mount_path, metafunc.module.__name__)

    if 'size' in metafunc.fixturenames:
        module_sizes = {
            'cli.mount.operation.test_fallocate': [2, 11 * MB],
            'cli.mount.operation.test_truncate': [2, 11 * MB]
        }
        function_sizes = module_sizes.get(metafunc.module.__name__, [])
        metafunc.parametrize('size,local_file,mount_file',
                             zip(function_sizes,
                                 map(lambda size: os.path.join(local_path, as_literal(size)), function_sizes),
                                 map(lambda size: os.path.join(mount_path, as_literal(size)), function_sizes)),
                             ids=map(as_literal, function_sizes))


def pytest_sessionstart(session):
    session.config.local_path = local_path = _get_local_path(config=session.config)
    session.config.mount_path = mount_path = _get_mount_path(config=session.config)
    sizes = _get_sizes(config=session.config)

    api = os.environ['API']
    api = api if not api.endswith('/') else api[:-1]
    api_token = os.environ['API_TOKEN']
    storage_region = os.environ['CP_TEST_REGION_ID']
    storage_folder = os.getenv('CP_TEST_FOLDER_ID')
    storage_provider = os.environ['CP_PROVIDER']
    storage_type = _get_storage_type(config=session.config, storage_provider=storage_provider)
    storage_name = _generate_storage_name(storage_type=storage_type, storage_region=storage_region)
    storage_path = storage_name
    session.config.storage_name = storage_name

    logs_path = os.path.join(root_logs_path, storage_name)
    start_log_path = os.path.join(logs_path, 'start.log')

    mkdir(local_path, mount_path, logs_path)

    if storage_type in ['S3', 'AZ', 'GS']:
        session.config.storage_mount_path = mount_path
        execute(log_path=start_log_path, command="""
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
        
        pipe storage mount -t -l '{logs_path}/mount.log' -b '{storage_name}' '{mount_path}'
        
        counter=0
        while ! mount | grep '{mount_path}' > /dev/null && [ "$counter" -lt "10" ]; do
            counter=$((counter+1))
            sleep 1
        done
        if ! mount | grep '{mount_path}' > /dev/null
        then
            echo "Mount at {mount_path} is not accessible"
            exit 1
        fi
        """.format(storage_name=storage_name,
                   storage_path=storage_path,
                   storage_type=storage_type,
                   region=storage_region,
                   folder=storage_folder or '',
                   logs_path=logs_path,
                   source_size=sizes[-1],
                   source_path=tests_source_path,
                   local_path=local_path,
                   mount_path=mount_path))
    else:
        storage_share = os.getenv('CP_TEST_SHARE_ID')
        storage_mount_path = os.path.join(mount_path, storage_name.replace('-', '_'))
        session.config.storage_mount_path = storage_mount_path

        execute(log_path=start_log_path, command="""
        head -c '{source_size}' /dev/urandom > '{source_path}'
        
        wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O jq
        chmod +x jq
        curl -k -X GET \
             --header 'Content-Type: application/json' \
             --header 'Accept: application/json' \
             --header 'Authorization: Bearer {api_token}' \
             '{api}/cloud/region/{region}' \
             > {logs_path}/curl-get-region.log
        SHARE_ROOT=$(cat {logs_path}/curl-get-region.log \
                     | ./jq -r '.payload.fileShareMounts[] | select(.id=='"{share}"') | .mountRoot')
        curl -k -X POST \
             --header 'Content-Type: application/json' \
             --header 'Accept: application/json' \
             --header 'Authorization: Bearer {api_token}' \
             -d '{{ \
                 "fileShareMountId": "{share}", \
                 "name": "{storage_name}", \
                 "path": "'"$SHARE_ROOT"':/{storage_path}", \
                 "parentFolderId": "{folder}", \
                 "regionId": "{region}", \
                 "serviceType": "FILE_SHARE" \
              }}' \
              '{api}/datastorage/save?cloud=true&skipPolicy=false' \
              > {logs_path}/curl-create-storage.log
        
        pipe storage mount -t -l '{logs_path}/mount.log' -f '{mount_path}'
        
        counter=0
        while ! mount | grep '{mount_path}' > /dev/null && [ "$counter" -lt "10" ]; do
            counter=$((counter+1))
            sleep 1
        done
        if ! mount | grep '{mount_path}' > /dev/null
        then
            echo "Mount at {mount_path} is not accessible"
            exit 1
        fi
        
        counter=0
        while [ ! -d "{storage_mount_path}" ] && [ "$counter" -lt "70" ]; do
            counter=$((counter+1))
            sleep 1
        done
        if [ ! -d "{storage_mount_path}" ]
        then
            echo "Mount directory at {storage_mount_path} is not accessible"
            exit 1
        fi
        """.format(api=api,
                   api_token=api_token,
                   storage_name=storage_name,
                   storage_path=storage_path,
                   storage_mount_path=storage_mount_path,
                   storage_type=storage_type,
                   region=storage_region,
                   share=storage_share,
                   folder=storage_folder or '',
                   logs_path=logs_path,
                   source_size=sizes[-1],
                   source_path=tests_source_path,
                   local_path=local_path,
                   mount_path=mount_path))


def pytest_sessionfinish(session, exitstatus):
    local_path = _get_local_path(config=session.config)
    mount_path = _get_mount_path(config=session.config)
    logs_path = os.path.join(root_logs_path, session.config.storage_name)
    finish_log_path = os.path.join(logs_path, 'finish.log')
    execute(log_path=finish_log_path, command="""
    if [ -d "{local_path}" ]; then rm -rf '{local_path}'/*; fi
    if [ -f "{source_path}" ]; then rm -f {source_path}; fi
    pipe storage umount '{mount_path}'
    pipe storage delete -y -c -n '{storage_name}'
    """.format(storage_name=session.config.storage_name,
               local_path=local_path,
               mount_path=mount_path,
               source_path=tests_source_path))


def _get_storage_type(config, storage_provider):
    if config.option.webdav:
        storage_kind = 'webdav'
    elif config.option.object:
        storage_kind = 'object'
    else:
        storage_kind = default_storage_kind
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


def _get_local_path(config):
    return config.option.local or default_local_path


def _get_mount_path(config):
    return config.option.mount or default_mount_path


def _get_sizes(config):
    raw_sizes_string = config.option.sizes or default_sizes
    return sorted(as_size(raw_size.strip())
                  for raw_size in raw_sizes_string.split(',')
                  if raw_size.strip())


def _generate_storage_name(storage_type, storage_region):
    return 'e2e-mount-{type}-{region}-{time}' \
        .format(type=storage_type, region=storage_region, time=datetime.now().strftime('%d-%m-%Y-%H-%M-%S')) \
        .lower()
