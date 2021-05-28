# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging
import os
import pathlib
import subprocess
import traceback
import zipfile
import sys
import tarfile


class LaunchError(RuntimeError):
    pass


def _mkdir(path):
    pathlib.Path(path).mkdir(parents=True, exist_ok=True)


def _install_python_packages(packages, index_url=None, trusted_host=None):
    install_command = [sys.executable, '-m', 'pip', 'install', '-q']
    if index_url:
        install_command += ['--index-url', index_url]
    if trusted_host:
        install_command += ['--trusted-host', trusted_host]
    install_command += list(packages)
    subprocess.check_call(install_command)


def _download_file(source_url, target_path):
    r = requests.get(source_url, verify=False)
    with open(target_path, 'wb') as f:
        f.write(r.content)


def _extract_archive(source_path, target_path):
    if source_path.endswith('.tar.gz') or source_path.endswith('.tgz'):
        with tarfile.open(source_path, 'r:gz') as f:
            f.extractall(path=target_path)
    elif source_path.endswith('.zip'):
        with zipfile.ZipFile(source_path, 'r') as f:
            f.extractall(path=target_path)
    else:
        raise LaunchError(f'Unsupported archive type: {source_path}')


def _escape_backslashes(string):
    return string.replace('\\', '\\\\')


def _extract_boolean_flag(env_var_name):
    flag_value = os.environ[env_var_name] = os.environ.get(env_var_name, 'false')
    return flag_value.lower() == 'true'


def _parse_host_and_port(url, default_host, default_port):
    parsed_url = urlparse(url)
    host_and_port = parsed_url.netloc if parsed_url.netloc else parsed_url.path
    host_and_port = host_and_port.split(':')
    host = host_and_port[0] if host_and_port[0] else default_host
    port = int(host_and_port[1]) if len(host_and_port) > 1 and host_and_port[1] else default_port
    return host, port


if __name__ == '__main__':
    logging_format = os.environ['CP_LOGGING_FORMAT'] = \
        os.getenv('CP_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')
    logging_level = os.environ['CP_LOGGING_LEVEL'] = os.getenv('CP_LOGGING_LEVEL', 'INFO')
    host_root = os.environ['CP_HOST_ROOT_DIR'] = os.getenv('CP_HOST_ROOT_DIR', 'c:\\host')
    runs_root = os.environ['CP_RUNS_ROOT_DIR'] = os.getenv('CP_RUNS_ROOT_DIR', 'c:\\runs')
    run_id = os.environ['RUN_ID'] = os.getenv('RUN_ID', '0')
    pipeline_name = os.environ['PIPELINE_NAME'] = os.getenv('PIPELINE_NAME', 'DefaultPipeline')
    run_dir = os.environ['RUN_DIR'] = os.getenv('RUN_DIR', os.path.join(runs_root, pipeline_name + '-' + run_id))
    common_repo_dir = os.environ['COMMON_REPO_DIR'] = os.getenv('COMMON_REPO_DIR', os.path.join(run_dir, 'CommonRepo'))
    log_dir = os.environ['LOG_DIR'] = os.getenv('LOG_DIR', os.path.join(run_dir, 'logs'))
    pipe_dir = os.environ['PIPE_DIR'] = os.getenv('PIPE_DIR', os.path.join(run_dir, 'pipe'))
    analysis_dir = os.environ['ANALYSIS_DIR'] = os.getenv('ANALYSIS_DIR', os.path.join(run_dir, 'analysis'))
    resources_dir = os.environ['RESOURCES_DIR'] = os.getenv('RESOURCES_DIR', os.path.join(run_dir, 'resources'))
    distribution_url = os.environ['DISTRIBUTION_URL'] = os.getenv('DISTRIBUTION_URL')
    api_url = os.environ['API'] = \
        os.getenv('API', 'https://cp-api-srv.default.svc.cluster.local:31080/pipeline/restapi/')
    api_token = os.environ['API_TOKEN'] = os.getenv('API_TOKEN')
    node_owner = os.environ['CP_NODE_OWNER'] = os.getenv('CP_NODE_OWNER', 'Administrator')
    edge_url = os.environ['EDGE'] = os.getenv('EDGE', 'https://cp-edge.default.svc.cluster.local:31081')
    node_private_key_path = os.environ['CP_NODE_PRIVATE_KEY'] = \
        os.getenv('CP_NODE_PRIVATE_KEY', os.path.join(host_root, '.ssh', 'id_rsa'))
    owner = os.environ['OWNER'] = os.getenv('OWNER')
    owner_password = os.environ['OWNER_PASSWORD'] = os.getenv('OWNER_PASSWORD', os.getenv('SSH_PASS'))
    owner_groups = os.environ['OWNER_GROUPS'] = os.getenv('OWNER_GROUPS', 'Administrators')
    logon_title = os.environ['CP_LOGON_TITLE'] = os.getenv('CP_LOGON_TITLE', 'Login as ' + owner)
    logon_image_url = os.environ['CP_LOGON_IMAGE_URL'] = \
        os.getenv('CP_LOGON_IMAGE_URL',
                  'https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pgina/logon.bmp')
    logon_image_path = os.environ['CP_LOGON_IMAGE_PATH'] = \
        os.getenv('CP_LOGON_IMAGE_PATH', os.path.join(resources_dir, 'logon.bmp'))
    task_path = os.environ['CP_TASK_PATH'] = os.getenv('CP_TASK_PATH', '.\\task.ps1')
    python_dir = os.environ['CP_PYTHON_DIR'] = os.environ.get('CP_PYTHON_DIR', 'c:\\python')
    requires_repo = os.environ['CP_REPO_ENABLED'] = os.getenv('CP_REPO_ENABLED', 'false')
    requires_repo = requires_repo.lower() == 'true'
    repo_pypi_base_url = os.environ['CP_REPO_PYPI_BASE_URL_DEFAULT'] = \
        os.getenv('CP_REPO_PYPI_BASE_URL_DEFAULT',
                  'http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple')
    repo_pypi_trusted_host = os.environ['CP_REPO_PYPI_TRUSTED_HOST_DEFAULT'] = \
        os.getenv('CP_REPO_PYPI_TRUSTED_HOST_DEFAULT',
                  'cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com')
    requires_cloud_data = _extract_boolean_flag('CP_CAP_WIN_INSTALL_CLOUD_DATA')
    cloud_data_distribution_url = \
        os.environ['CP_CLOUD_DATA_WIN_DISTRIBUTION_URL'] = \
        os.getenv('CP_CLOUD_DATA_WIN_DISTRIBUTION_URL',
                  'https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/cloud-data/win/cloud-data-win-x64.zip')
    requires_drive_mount = _extract_boolean_flag('CP_CAP_WIN_MOUNT_DRIVE')

    logging.basicConfig(level=logging_level, format=logging_format)

    logging.info('Creating system directories...')
    _mkdir(run_id)
    _mkdir(common_repo_dir)
    _mkdir(log_dir)
    _mkdir(pipe_dir)
    _mkdir(analysis_dir)
    _mkdir(resources_dir)

    logging.info('Configuring cloud pipeline repositories...')
    if not requires_repo:
        repo_pypi_base_url = None
        repo_pypi_trusted_host = None

    logging.info('Installing python packages...')
    _install_python_packages(['urllib3==1.25.9', 'requests==2.22.0'],
                             index_url=repo_pypi_base_url, trusted_host=repo_pypi_trusted_host)
    import urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    import requests

    logging.info('Downloading pipe common...')
    _download_file(distribution_url + 'pipe-common.tar.gz', os.path.join(common_repo_dir, 'pipe-common.tar.gz'))

    logging.info('Unpacking pipe common...')
    _extract_archive(os.path.join(common_repo_dir, 'pipe-common.tar.gz'), common_repo_dir)

    logging.info('Installing pipe common...')
    _install_python_packages([common_repo_dir],
                             index_url=repo_pypi_base_url, trusted_host=repo_pypi_trusted_host)

    from pipeline.api import PipelineAPI
    from pipeline.log.logger import CloudPipelineLogger
    from pipeline.utils.ssh import HostSSH
    from pipeline.utils.path import add_to_path

    logging.getLogger('paramiko').setLevel(logging.WARNING)

    api = PipelineAPI(api_url=api_url, log_dir=log_dir)
    api_logger = CloudPipelineLogger(api=api, run_id=run_id)

    logging.info('Configuring PATH...')
    add_to_path(os.path.join(common_repo_dir, 'powershell'))
    add_to_path(pipe_dir)

    logging.info('Downloading pipe...')
    _download_file(distribution_url + 'pipe.zip', os.path.join(pipe_dir, 'pipe.zip'))

    logging.info('Unpacking pipe...')
    _extract_archive(os.path.join(pipe_dir, 'pipe.zip'), os.path.dirname(pipe_dir))

    logging.info('Configuring pipe...')
    subprocess.check_call(f'powershell -Command "pipe.exe configure --api \'{api_url}\''
                          f'                                        --auth-token \'{api_token}\''
                          f'                                        --timezone local'
                          f'                                        --proxy pac"')

    logging.info('Preparing for SSH connections to the node...')
    run = api.load_run_efficiently(run_id)
    node_ip = os.environ['NODE_IP'] = run.get('instance', {}).get('nodeIP', '')
    node_ssh = HostSSH(host=node_ip, private_key_path=node_private_key_path, logger=api_logger)

    logging.info('Installing pipe common on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -m pip install -q {common_repo_dir}',
                     user=node_owner)

    logging.info('Configuring PATH on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from pipeline.utils.path import add_to_path; '
                     f'add_to_path(\'{_escape_backslashes(python_dir)}\'); '
                     f'add_to_path(\'{_escape_backslashes(os.path.join(common_repo_dir, "powershell"))}\'); '
                     f'add_to_path(\'{_escape_backslashes(pipe_dir)}\')\\"',
                     user=node_owner)

    logging.info('Configuring owner account on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from pipeline.utils.account import create_user; '
                     f'create_user(\'{owner}\', \'{owner_password}\', \'{owner_groups}\')\\"',
                     user=node_owner)

    logging.info('Downloading seamless logon image...')
    _download_file(logon_image_url, logon_image_path)

    logging.info('Configuring seamless logon on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from scripts.configure_seamless_logon_win import configure_seamless_logon_win; '
                     f'configure_seamless_logon_win(\'{owner}\', \'{owner_password}\', \'{owner_groups}\', '
                     f'                             \'{logon_title}\', \'{_escape_backslashes(logon_image_path)}\')\\"',
                     user=node_owner)

    logging.info('Configuring system settings on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from scripts.configure_system_settings_win import configure_system_settings_win; '
                     f'configure_system_settings_win()\\"',
                     user=node_owner)

    logging.info('Restarting logon processes on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from pipeline.utils.proc import terminate_processes; '
                     f'terminate_processes(\'winlogon.exe\')\\"',
                     user=node_owner)

    if requires_cloud_data:
        install_cloud_data_task = 'InstallCloudData'
        api_logger.info('Installing Cloud-Data application...', task=install_cloud_data_task)
        api_logger.info('Downloading Cloud-Data App...', task=install_cloud_data_task)
        _download_file(cloud_data_distribution_url, os.path.join(run_dir, 'cloud-data.zip'))

        api_logger.info('Unpacking Cloud-Data App...', task=install_cloud_data_task)
        _extract_archive(os.path.join(run_dir, 'cloud-data.zip'), run_dir)

        api_logger.info('Configuring Cloud-Data App on the node...', task=install_cloud_data_task)
        node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                         f'from scripts.configure_cloud_data_win import configure_cloud_data_win; '
                         f'configure_cloud_data_win(\'{_escape_backslashes(run_dir)}\', \'{edge_url}\','
                         f'                         \'{owner}\', \'{owner}\', \'{api_token}\')\\"',
                         output_task=install_cloud_data_task, user=node_owner)
        api_logger.success('Cloud-Data installed and configured successfully!', task=install_cloud_data_task)

    logging.info('Configuring pipe on the node...')
    node_ssh.execute(f'pipe configure --api \'{api_url}\' --auth-token \'{api_token}\' --timezone local --proxy pac',
                     user=owner)

    logging.info('Configuring node SSH server proxy...')
    subprocess.check_call(f'powershell -Command "pipe.exe tunnel start --direct -lp 22 -rp 22 --trace '
                          f'-l {_escape_backslashes(os.path.join(run_dir, "ssh_proxy.log"))} '
                          f'{node_ip}"')

    if requires_drive_mount:
        drive_mapping_task = 'NetworkStorageMapping'
        api_logger.info('Adding EDGE root certificate to trusted...', task=drive_mapping_task)
        edge_root_cert_path = os.path.join(host_root, 'edge_root.cer')
        from urllib.parse import urlparse
        edge_host, edge_port = _parse_host_and_port(edge_url, 'cp-edge.default.svc.cluster.local', 31081)
        node_ssh.execute(f'{python_dir}\\python.exe -c \\"from pipeline.utils.pki import save_root_cert;'
                         f' save_root_cert(\'{edge_host}\', {edge_port}, \'{edge_root_cert_path}\')\\"',
                         output_task=drive_mapping_task, user=node_owner)
        node_ssh.execute(f'ImportCertificate -FilePath "\'{edge_root_cert_path}\'"'
                         f' -CertStoreLocation Cert:\\\\LocalMachine\\\\Root',
                         output_task=drive_mapping_task, user=node_owner)
        api_logger.info('Preparing environment for storage mapping...', task=drive_mapping_task)
        node_ssh.execute(f'InitializeEnvironmentToMountDrive | Out-Null',
                         output_task=drive_mapping_task, user=node_owner)

        api_logger.info('Mapping network storage...', task=drive_mapping_task)
        mounting_script_path = _escape_backslashes(os.path.join(common_repo_dir, 'powershell\\MountDrive.ps1'))
        node_ssh.execute(f'RegisterMountingTask -UserName \\"{owner}\\"'
                         f' -BearerToken \\"{api_token}\\"'
                         f' -EdgeHost \\"{edge_host}\\"'
                         f' -EdgePort \\"{edge_port}\\"'
                         f' -MountingScript \\"{mounting_script_path}\\"'
                         f' | Out-Null',
                         output_task=drive_mapping_task, user=node_owner)
        api_logger.success('Drive mapping performed successfully!', task=drive_mapping_task)

    api_logger.success('Environment initialization finished', task='InitializeEnvironment')

    logging.info('Executing task...')
    task_wrapping_command = f'powershell -Command "& {task_path} -ErrorAction Stop"'
    logging.info(f'Task command: {task_wrapping_command}')
    try:
        exit_code = subprocess.call(task_wrapping_command, cwd=analysis_dir)
    except:
        traceback.print_exc()
        exit_code = 1
    logging.info('Finalizing task execution...')
    logging.info(f'Exiting with {exit_code}...')
    sys.exit(exit_code)
