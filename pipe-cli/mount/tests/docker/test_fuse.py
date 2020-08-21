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


def test_fuse(distribution, storage, folder, pytest_arguments):
    storage_name = 'fuse-temp-%s-%s-%s-%s' % (distribution, storage.type, storage.region, datetime.now().strftime('%d-%m-%Y_%H-%M-%S'))
    container_name = storage_name.replace(':', '-').replace('/', '-')
    current_host_logs_path = os.path.join(host_logs_path, container_name)
    current_host_root_log_path = os.path.join(current_host_logs_path, 'container.log')
    current_container_logs_path = os.path.join(container_logs_path, container_name)
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
        """.format(container_name=container_name,
                   api=os.environ['API'], api_token=os.environ['API_TOKEN'],
                   host_pipe_path=host_pipe_path, container_pipe_path=container_pipe_path,
                   host_tests_path=host_tests_path, container_tests_path=container_tests_path,
                   host_logs_path=host_logs_path, container_logs_path=container_logs_path,
                   host_command_path=host_command_path,
                   distribution=distribution),
                log_path=current_host_root_log_path)
    finally:
        os.remove(host_command_path)
