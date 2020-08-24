import os
import tempfile
from datetime import datetime

from utils import MB, execute, as_size, assert_content

host_root_path = os.getcwd()
host_pipe_path = os.path.join(host_root_path, 'pipe')
host_tests_path = host_root_path
host_logs_path = os.path.join(host_root_path, 'logs')
container_root_path = os.path.join(os.sep, 'workdir')
container_pipe_path = os.path.join(host_root_path, 'pipe')
container_tests_path = os.path.join(container_root_path, 'tests')
container_logs_path = os.path.join(container_root_path, 'logs')
container_mount_path = os.path.join(container_root_path, 'mount')


def test_fuse_storage(distribution, storage, folder, pytest_arguments):
    storage_name = generate_storage_name(image=distribution.name, region=storage.region, type=storage.type)
    current_host_logs_path = os.path.join(host_logs_path, storage_name)
    current_host_root_log_path = os.path.join(current_host_logs_path, 'container.log')
    current_container_logs_path = os.path.join(container_logs_path, storage_name)
    current_container_mount_log_path = os.path.join(current_container_logs_path, 'mount.log')
    command = """
    set -e
    chmod +x '{pipe}'
    '{pipe}' storage create -c \
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
    '{pipe}' storage mount -l '{log_path}' -b '{storage_name}' '{mounted_path}'
    cp -r '{tests_path}' /tests
    rm -rf /tests/.pytest_cache
    cd /tests
    export PYTHONPATH=$PWD:$PYTHONPATH
    pip install pytest
    pytest -s -v local {pytest_arguments}
    {pipe} storage delete -n {storage_name} -y -c
    """.format(pipe=host_pipe_path,
               storage_name=storage_name,
               storage_path=storage_name,
               storage_type=storage.type,
               region=storage.region,
               folder=folder or '',
               log_path=current_container_mount_log_path,
               mounted_path=container_mount_path,
               tests_path=container_tests_path,
               pytest_arguments=pytest_arguments)
    launch_containerized_tests(command, current_host_logs_path, current_host_root_log_path, distribution.image, storage_name)


def generate_storage_name(image, region, type):
    storage_name = 'pft_%s-%s-%s-%s' % (
        image, type, region, datetime.now().strftime('%d-%m-%Y_%H-%M-%S'))
    storage_name = storage_name.replace(':', '_').replace('-', '_').replace('/', '_')
    return storage_name


def test_fuse_webdav(distribution, storage, folder, pytest_arguments):
    storage_name = generate_storage_name(image=distribution.name, region=storage.region, type='WebDav')
    current_host_logs_path = os.path.join(host_logs_path, storage_name)
    current_host_root_log_path = os.path.join(current_host_logs_path, 'container.log')
    current_container_logs_path = os.path.join(container_logs_path, storage_name)
    current_container_mount_log_path = os.path.join(current_container_logs_path, 'mount.log')
    command = """
    set -e
    chmod +x '{pipe}'
    wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq
    chmod +x /usr/bin/jq
    SHARE_ROOT=$(curl -k -X GET \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json' \
        --header 'Authorization: Bearer {api_token}' \
         '{api}cloud/region/{region}' \
         | jq -r '.payload.fileShareMounts[] | select(.id==2) | .mountRoot')
    curl -k -X POST \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json' \
        --header 'Authorization: Bearer {api_token}' \
        -d '{{ \
           "fileShareMountId": "{share_mount_id}", \
           "name": "{storage_name}", \
           "path": "'"$SHARE_ROOT"':/{storage_path}", \
           "parentFolderId": "{folder}", \
           "regionId": "{region}", \
           "serviceType": "FILE_SHARE" \
         }}' \
         '{api}datastorage/save?cloud=true&skipPolicy=false'
    '{pipe}' storage mount -l '{log_path}' -f '{mounted_path}'
    cp -r '{tests_path}' /tests
    rm -rf /tests/.pytest_cache
    cd /tests
    export PYTHONPATH=$PWD:$PYTHONPATH
    pip install pytest
    STORAGE_ROOT_PATH="{mounted_path}/{storage_name}"
    STORAGE_MOUNT_PATH="$STORAGE_ROOT_PATH/mount"
    counter=0
    while [ ! -d "$STORAGE_ROOT_PATH" ]; do
      counter=$((counter+1))
      if [ $counter -gt 60 ]
      then
        echo "WebDAV mount at $STORAGE_ROOT_PATH not found"
        exit 1
      fi
      sleep 1
    done
    if [[ ! -d "$STORAGE_ROOT_PATH" ]]
    then
        echo "WebDAV mount at $STORAGE_ROOT_PATH not found"
        exit 1
    fi
    mkdir "$STORAGE_MOUNT_PATH"
    pytest -s -v local {pytest_arguments} --mount "$STORAGE_MOUNT_PATH"
    {pipe} storage delete -n {storage_name} -y -c
    """.format(api=os.getenv('API'),
               api_token=os.getenv('API_TOKEN'),
               share_mount_id=storage.id,
               region=storage.region,
               folder=folder or '',
               log_path=current_container_mount_log_path,
               mounted_path=container_mount_path,
               pipe=host_pipe_path,
               storage_name=storage_name,
               storage_path=storage_name,
               tests_path=container_tests_path,
               pytest_arguments=pytest_arguments)
    launch_containerized_tests(command, current_host_logs_path, current_host_root_log_path, distribution.image, storage_name)


def launch_containerized_tests(command, current_host_logs_path, current_host_root_log_path, image, storage_name):
    host_command_file_fd, host_command_path = tempfile.mkstemp()
    try:
        with os.fdopen(host_command_file_fd, 'w') as host_command_file:
            host_command_file.write(command)
        execute('mkdir -p \'%s\'' % current_host_logs_path)
        execute("""
        docker run --privileged \
                   --rm \
                   --name {container_name} \
                   -e TZ=UTC \
                   -e API={api} \
                   -e API_TOKEN={api_token} \
                   -v {host_pipe_path}:{container_pipe_path} \
                   -v {host_tests_path}:{container_tests_path} \
                   -v {host_logs_path}:{container_logs_path} \
                   -v {host_command_path}:/test.sh \
                   {distribution} \
                   bash test.sh
        """.format(container_name=storage_name,
                   api=os.environ['API'], api_token=os.environ['API_TOKEN'],
                   host_pipe_path=host_pipe_path, container_pipe_path=container_pipe_path,
                   host_tests_path=host_tests_path, container_tests_path=container_tests_path,
                   host_logs_path=host_logs_path, container_logs_path=container_logs_path,
                   host_command_path=host_command_path,
                   distribution=image),
                log_path=current_host_root_log_path)
    finally:
        os.remove(host_command_path)
