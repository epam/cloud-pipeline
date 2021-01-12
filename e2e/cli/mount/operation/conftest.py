import os
from datetime import datetime

import pytest

from ..utils import as_literal, execute, mkdir, KB, MB, MiB
from pyfs import rm

root_path = os.getcwd()

default_storage_kind = 'object'
default_local_path = os.path.join(root_path, 'e2e-local')
default_root_mount_path = os.path.join(root_path, 'e2e-mount')
default_logs_path = os.path.join(root_path, 'e2e-logs')
default_source_path = os.path.join(root_path, 'e2e-random')
default_chunk_size = 10 * MB
default_buffer_size = 512 * MB
default_small_sizes = {
    'cli.mount.operation.test_fallocate': [1],
    'cli.mount.operation.test_truncate': [1],
    'cli.mount.operation.test_read': [1],
    'cli.mount.operation.test_write': [1]
}
default_sizes = {
    'cli.mount.operation.test_fallocate': [1, 11, 11 * MB],
    'cli.mount.operation.test_truncate': [1, 11, 11 * MB],
    'cli.mount.operation.test_read': [0, 1, 11 * MB, 600 * MB],
    'cli.mount.operation.test_write': [0, 1, 1 * KB, 1 * MB, 1 * MiB,
                                       default_chunk_size,
                                       default_chunk_size * 4 + 1 * MB,
                                       default_buffer_size,
                                       default_buffer_size + 1 * MB]
}


def pytest_addoption(parser):
    parser.addoption('--webdav', help='Executes tests against a webdav data storage.', action='store_true')
    parser.addoption('--object', help='Executes tests against an object data storage.', action='store_true')
    parser.addoption('--small', help='Executes tests for only small subset of file sizes.', action='store_true')


@pytest.fixture(scope='module', autouse=True)
def cleanup_before_module(request):
    rm(request.node.config.local_path, under=True, recursive=True, force=True)
    rm(request.node.config.mount_path, under=True, recursive=True, force=True)


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
    session.config.local_path = default_local_path
    session.config.root_mount_path = default_root_mount_path
    session.config.source_path = default_source_path
    session.config.chunk_size = default_chunk_size
    session.config.sizes = default_sizes if not session.config.option.small else default_small_sizes
    session.config.source_size = max(map(max, session.config.sizes.values()))

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
    session.config.logs_path = os.path.join(default_logs_path, session.config.storage_name)

    mkdir(session.config.local_path, session.config.root_mount_path, session.config.logs_path)

    if storage_type in ['S3', 'AZ', 'GS']:
        session.config.mount_path = session.config.root_mount_path
        execute(log_path=os.path.join(session.config.logs_path, 'start.log'), command="""
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
                   logs_path=session.config.logs_path,
                   source_size=session.config.source_size,
                   source_path=default_source_path,
                   local_path=local_path,
                   mount_path=session.config.root_mount_path))
    else:
        storage_share = os.getenv('CP_TEST_SHARE_ID')
        session.config.mount_path = os.path.join(session.config.root_mount_path, storage_name.replace('-', '_'))

        execute(log_path=os.path.join(session.config.logs_path, 'start.log'), command="""
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
        while [ ! -d "{root_mount_path}" ] && [ "$counter" -lt "70" ]; do
            counter=$((counter+1))
            sleep 1
        done
        if [ ! -d "{root_mount_path}" ]
        then
            echo "Mount directory at {root_mount_path} is not accessible"
            exit 1
        fi
        """.format(api=api,
                   api_token=api_token,
                   storage_name=storage_name,
                   storage_path=storage_path,
                   storage_type=storage_type,
                   region=storage_region,
                   share=storage_share,
                   folder=storage_folder or '',
                   logs_path=session.config.logs_path,
                   source_size=session.config.source_size,
                   source_path=default_source_path,
                   local_path=session.config.local_path,
                   mount_path=session.config.mount_path,
                   root_mount_path=session.config.root_mount_path))


def pytest_sessionfinish(session, exitstatus):
    execute(log_path=os.path.join(session.config.logs_path, 'finish.log'), command="""
    pipe storage umount '{root_mount_path}'
    pipe storage delete -y -c -n '{storage_name}'
    if [ -f "{source_path}" ]; then rm -f "{source_path}"; fi
    if [ -d "{local_path}" ]; then rm -rf "{local_path}"; fi
    if [ -d "{root_mount_path}" ]; then rm -rf "{root_mount_path}"; fi
    """.format(storage_name=session.config.storage_name,
               source_path=session.config.source_path,
               local_path=session.config.local_path,
               root_mount_path=session.config.root_mount_path))


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


def _generate_storage_name(storage_type, storage_region):
    return 'e2e-mount-{type}-{region}-{time}' \
        .format(type=storage_type, region=storage_region, time=datetime.now().strftime('%d-%m-%Y-%H-%M-%S')) \
        .lower()
