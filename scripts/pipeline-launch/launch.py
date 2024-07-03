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
import re
import subprocess
import tarfile
import traceback
import zipfile
from urllib.parse import urlparse

import sys


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


def _extract_parameter(name, default='', default_provider=lambda: ''):
    parameter = os.environ[name] = os.getenv(name, default) or default_provider() or default
    return parameter


def _extract_boolean_parameter(name, default='false', default_provider=lambda: 'false'):
    parameter = _extract_parameter(name, default=default, default_provider=default_provider)
    return parameter.lower() == 'true'


def _extract_pypi_base_url(global_distribution_url):
    website_distribution_url_match = re.search('^https?://(.*)\.s3\.(.*)\.amazonaws\.com(.*)', global_distribution_url)
    if website_distribution_url_match:
        website_distribution_url = 'http://{storage_name}.s3-website.{storage_region}.amazonaws.com{storage_path}' \
            .format(storage_name=website_distribution_url_match.group(1),
                    storage_region=website_distribution_url_match.group(2),
                    storage_path=website_distribution_url_match.group(3))
        return f'{website_distribution_url}tools/python/pypi/simple'
    return None


def _parse_host_and_port(url, default_host, default_port):
    parsed_url = urlparse(url)
    host_and_port = parsed_url.netloc if parsed_url.netloc else parsed_url.path
    host_and_port = host_and_port.split(':')
    host = host_and_port[0] if host_and_port[0] else default_host
    port = int(host_and_port[1]) if len(host_and_port) > 1 and host_and_port[1] else default_port
    return host, port


try:
    logging_format = _extract_parameter('CP_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    logging_level = _extract_parameter('CP_LOGGING_LEVEL', default='INFO')
    host_root = _extract_parameter('CP_HOST_ROOT_DIR', default='c:\\host')
    runs_root = _extract_parameter('CP_RUNS_ROOT_DIR', default='c:\\runs')
    run_id = _extract_parameter('RUN_ID', default='0')
    pipeline_name = _extract_parameter('PIPELINE_NAME', default='DefaultPipeline')
    run_dir = _extract_parameter('RUN_DIR', default=os.path.join(runs_root, pipeline_name + '-' + run_id))
    common_repo_dir = _extract_parameter('COMMON_REPO_DIR', default=os.path.join(run_dir, 'CommonRepo'))
    log_dir = _extract_parameter('LOG_DIR', default=os.path.join(run_dir, 'logs'))
    tmp_dir = _extract_parameter('TMP_DIR', default=os.path.join(run_dir, 'tmp'))
    pipe_dir = _extract_parameter('PIPE_DIR', default=os.path.join(run_dir, 'pipe'))
    analysis_dir = _extract_parameter('ANALYSIS_DIR', default=os.path.join(run_dir, 'analysis'))
    resources_dir = _extract_parameter('RESOURCES_DIR', default=os.path.join(run_dir, 'resources'))
    distribution_url = _extract_parameter('DISTRIBUTION_URL',
                                          default='https://cp-api-srv.default.svc.cluster.local:31080/pipeline/')
    global_distribution_url = _extract_parameter(
        'GLOBAL_DISTRIBUTION_URL',
        default='https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/')
    api_url = _extract_parameter('API', default='https://cp-api-srv.default.svc.cluster.local:31080/pipeline/restapi/')
    api_token = _extract_parameter('API_TOKEN')
    node_owner = _extract_parameter('CP_NODE_OWNER', default='Administrator')
    edge_url = _extract_parameter('EDGE', default='https://cp-edge.default.svc.cluster.local:31081')
    node_private_key_path = _extract_parameter('CP_NODE_PRIVATE_KEY', default=os.path.join(host_root, '.ssh', 'id_rsa'))
    persisted_env_path = _extract_parameter('CP_ENV_FILE_TO_SOURCE', default=os.path.join(host_root, 'cp_env.ps1'))
    owner = _extract_parameter('OWNER', 'USER')
    owner = owner.split('@')[0]
    owner_password = _extract_parameter('OWNER_PASSWORD', default=os.getenv('SSH_PASS', ''))
    owner_groups = _extract_parameter('OWNER_GROUPS', default='Administrators')
    logon_title = _extract_parameter('CP_LOGON_TITLE', default='Login as ' + owner)
    logon_image_url = _extract_parameter('CP_LOGON_IMAGE_URL',
                                         default=global_distribution_url + 'tools/pgina/logon.bmp')
    logon_image_path = _extract_parameter('CP_LOGON_IMAGE_PATH', default=os.path.join(resources_dir, 'logon.bmp'))
    task_path = _extract_parameter('CP_TASK_PATH', '.\\task.ps1')
    python_dir = _extract_parameter('CP_PYTHON_DIR', 'c:\\python')
    # todo: Enable support for custom repo usage once launch with default parameters issue is fixed in GUI
    requires_repo = False
    repo_pypi_base_url = _extract_parameter(
        'CP_REPO_PYPI_BASE_URL_DEFAULT',
        default_provider=lambda: _extract_pypi_base_url(global_distribution_url),
        default='http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/tools/python/pypi/simple')
    repo_pypi_trusted_host = _extract_parameter('CP_REPO_PYPI_TRUSTED_HOST_DEFAULT',
                                                default_provider=lambda: urlparse(repo_pypi_base_url).netloc)

    # Enables network file systems and object storages mounting
    requires_storage_mount = _extract_boolean_parameter('CP_CAP_WIN_MOUNT_STORAGE')

    # Specifies network file systems and object storages mounting root directory
    storage_mount_root = _extract_parameter('CP_STORAGE_MOUNT_ROOT_DIR', default='c:\\cloud-data')

    # Specifies Amazon S3 object storages fuse type
    _extract_parameter('CP_S3_FUSE_TYPE', default='pipefuse')

    # Specifies Google Cloud object storages fuse type
    _extract_parameter('CP_GCS_FUSE_TYPE', default='pipefuse')

    # Enables Cloud Data App initialization
    requires_cloud_data = _extract_boolean_parameter('CP_CAP_WIN_INSTALL_CLOUD_DATA')

    cloud_data_distribution_url = _extract_parameter(
        'CP_CLOUD_DATA_WIN_DISTRIBUTION_URL',
        default=global_distribution_url + 'tools/cloud-data/win/cloud-data-win-x64.zip')

    # Enables network file systems mounting as drives
    requires_drive_mount = _extract_boolean_parameter('CP_CAP_WIN_MOUNT_DRIVE')

    # Allows to apply multiple parameters to nomachine server configuration
    # For example: ConnectionsLimit=1,ConnectionsUserLimit=1
    nomachine_server_parameters = _extract_parameter('CP_CAP_DESKTOP_NM_SERVER_PARAMETERS')

    # Enables the usage of nomachine specific user connection files rather than run owner connection files
    _extract_boolean_parameter('CP_CAP_DESKTOP_NM_USER_CONNECTION_FILES', default='true')

    logging.basicConfig(level=logging_level, format=logging_format)

    logging.info('Creating system directories...')
    _mkdir(run_id)
    _mkdir(common_repo_dir)
    _mkdir(log_dir)
    _mkdir(tmp_dir)
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
    from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger
    from pipeline.utils.ssh import RemoteHostExecutor, LoggingExecutor, UserExecutor
    from pipeline.utils.path import add_to_path

    logging.getLogger('paramiko').setLevel(logging.WARNING)

    api = PipelineAPI(api_url=api_url, log_dir=log_dir)
    logger = LocalLogger()
    run_logger = RunLogger(api=api, run_id=run_id, inner=logger)

    logger.info('Configuring PATH...')
    add_to_path(os.path.join(common_repo_dir, 'powershell'))
    add_to_path(pipe_dir)

    logger.info('Downloading pipe...')
    _download_file(distribution_url + 'pipe.zip', os.path.join(pipe_dir, 'pipe.zip'))

    logger.info('Unpacking pipe...')
    _extract_archive(os.path.join(pipe_dir, 'pipe.zip'), os.path.dirname(pipe_dir))

    logger.info('Configuring pipe...')
    subprocess.check_call(f'powershell -Command "pipe configure --api \'{api_url}\''
                          f'                                        --auth-token \'{api_token}\''
                          f'                                        --timezone local'
                          f'                                        --proxy pac"')

    logger.info('Preparing for SSH connections to the node...')
    node_ip = _extract_parameter(
        'NODE_IP',
        default_provider=lambda: api.load_run_efficiently(run_id).get('instance', {}).get('nodeIP', ''))
    node_ssh = RemoteHostExecutor(host=node_ip, private_key_path=node_private_key_path)
    node_ssh = LoggingExecutor(logger=logger, inner=node_ssh)
    node_ssh = UserExecutor(user=node_owner, inner=node_ssh)

    logger.info('Installing pipe common on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -m pip install -q {common_repo_dir}')

    logger.info('Configuring PATH on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from pipeline.utils.path import add_to_path; '
                     f'add_to_path(\'{_escape_backslashes(python_dir)}\'); '
                     f'add_to_path(\'{_escape_backslashes(os.path.join(common_repo_dir, "powershell"))}\'); '
                     f'add_to_path(\'{_escape_backslashes(pipe_dir)}\')\\"')

    logger.info('Configuring system settings on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from scripts.configure_system_settings_win import configure_system_settings_win; '
                     f'configure_system_settings_win()\\"')
    node_ssh.execute(f'ConfigureSystemSettings')

    logger.info('Configuring owner account on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from pipeline.utils.account import create_user; '
                     f'create_user(\'{owner}\', \'{owner_password}\', groups=\'{owner_groups}\')\\"')

    logger.info('Persisting environment...')
    with open(persisted_env_path, 'w') as f:
        f.write('\n'.join('$env:' + key + '="' + value + '"'
                          for key, value in os.environ.items()
                          if key.replace('_', '').isalnum()))

    if nomachine_server_parameters:
        logger.info('Configuring and restarting nomachine server...')
        node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                         f'from scripts.configure_nomachine_win import configure_nomachine_win; '
                         f'configure_nomachine_win(\'{nomachine_server_parameters}\')\\"')

    logger.info('Configuring nice dcv server...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from scripts.configure_nice_dcv_win import configure_nice_dcv_win; '
                     f'configure_nice_dcv_win(\'{_escape_backslashes(run_dir)}\', \'{owner}\')\\"')

    logger.info('Downloading seamless logon image...')
    _download_file(logon_image_url, logon_image_path)

    logger.info('Configuring seamless logon on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from scripts.configure_seamless_logon_win import configure_seamless_logon_win; '
                     f'configure_seamless_logon_win(\'{owner}\', \'{owner_password}\', \'{owner_groups}\', '
                     f'                             \'{logon_title}\', \'{_escape_backslashes(logon_image_path)}\')\\"')

    logger.info('Restarting logon processes on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                     f'from pipeline.utils.proc import terminate_processes; '
                     f'terminate_processes(\'winlogon.exe\')\\"')

    if requires_storage_mount:
        task_name = 'MountDataStorages'
        task_logger = TaskLogger(task=task_name, inner=run_logger)
        task_ssh = LoggingExecutor(logger=task_logger, inner=node_ssh)
        task_logger.info('Mounting data storages...')
        # Invocation of WmiMethod is required in order to keep background processes running after ssh session is over
        task_ssh.execute(f'Invoke-WmiMethod -Path \'Win32_Process\' -Name Create -ArgumentList \''
                         f'powershell.exe -c \". {persisted_env_path}; {python_dir}\\python.exe '
                         f'{os.path.join(common_repo_dir, "scripts", "mount_storage.py")} '
                         f'--mount-root {storage_mount_root} '
                         f'--tmp-dir {tmp_dir} '
                         f'--task {task_name}\"\' '
                         f'| ForEach-Object {{ '
                         f'     $p = get-process -id $_.ProcessId;'
                         f'     Wait-Process -Id $_.ProcessId;'
                         f'     exit $p.ExitCode'
                         f'}}')

    if requires_cloud_data:
        task_logger = TaskLogger(task='InstallCloudData', inner=run_logger)
        task_ssh = LoggingExecutor(logger=task_logger, inner=node_ssh)
        task_logger.info('Installing Cloud-Data application...')
        task_logger.info('Downloading Cloud-Data App...')
        _download_file(cloud_data_distribution_url, os.path.join(run_dir, 'cloud-data.zip'))

        task_logger.info('Unpacking Cloud-Data App...')
        _extract_archive(os.path.join(run_dir, 'cloud-data.zip'), run_dir)

        task_logger.info('Configuring Cloud-Data App on the node...')
        task_ssh.execute(f'{python_dir}\\python.exe -c \\"'
                         f'from scripts.configure_cloud_data_win import configure_cloud_data_win; '
                         f'configure_cloud_data_win(\'{_escape_backslashes(run_dir)}\', \'{edge_url}\','
                         f'                         \'{owner}\', \'{api_token}\')\\"')

        cloud_data_config_finalization_script_path = _escape_backslashes(
            os.path.join(common_repo_dir, 'powershell\\FinalizeCloudDataConfig.ps1'))
        cloud_data_config_tmp_path = os.path.join(run_dir, ".pipe-webdav-client")
        task_ssh.execute(f'RegisterCloudDataConfigurationTask -UserName \\"{owner}\\"'
                         f' -CloudDataConfigFolder \\"{_escape_backslashes(cloud_data_config_tmp_path)}\\"'
                         f' -FinalizingScript \\"{cloud_data_config_finalization_script_path}\\" | Out-Null')
        task_logger.success('Cloud-Data installed and configured successfully!')

    logger.info('Configuring pipe on the node...')
    node_ssh.execute(f'pipe configure --api \'{api_url}\' --auth-token \'{api_token}\' --timezone local --proxy pac',
                     user=owner)

    logger.info('Configuring node SSH server proxy...')
    subprocess.check_call(f'powershell -Command "pipe tunnel start --direct -lp 22 -rp 22 --trace '
                          f'-l {_escape_backslashes(os.path.join(run_dir, "ssh_proxy.log"))} '
                          f'{node_ip}"')

    if requires_drive_mount:
        task_logger = TaskLogger(task='NetworkStorageMapping', inner=run_logger)
        task_ssh = LoggingExecutor(logger=task_logger, inner=node_ssh)
        task_logger.info('Adding EDGE root certificate to trusted...')
        edge_root_cert_path = os.path.join(host_root, 'edge_root.cer')
        edge_host, edge_port = _parse_host_and_port(edge_url, 'cp-edge.default.svc.cluster.local', 31081)
        task_ssh.execute(f'{python_dir}\\python.exe -c '
                         f'\\"from scripts.add_root_certificate_win import add_root_cert_to_trusted_root_win;'
                         f'   add_root_cert_to_trusted_root_win(\'{edge_host}\', {edge_port})\\"')
        task_logger.info('Configuring environment for storage mapping...')
        task_ssh.execute(f'{python_dir}\\python.exe -c '
                         f'\\"from scripts.configure_drive_mount_env_win import configure_drive_mount_env_win; '
                         f'   configure_drive_mount_env_win(\'{owner}\', \'{edge_host}\')\\"')

        task_logger.info('Mapping network storage...')
        mounting_script_path = _escape_backslashes(os.path.join(common_repo_dir, 'powershell\\MountDrive.ps1'))
        task_ssh.execute(f'RegisterMountingTask -UserName \\"{owner}\\"'
                         f' -BearerToken \\"{api_token}\\"'
                         f' -EdgeHost \\"{edge_host}\\"'
                         f' -EdgePort \\"{edge_port}\\"'
                         f' -MountingScript \\"{mounting_script_path}\\"'
                         f' | Out-Null')
        task_logger.success('Drive mapping performed successfully!')

    run_logger.success('Environment initialization finished', task='InitializeEnvironment')

    logger.info('Executing task...')
    task_wrapping_command = f'powershell -Command "& {task_path} -ErrorAction Stop"'
    logger.info(f'Task command: {task_wrapping_command}')
    try:
        exit_code = subprocess.call(task_wrapping_command, cwd=analysis_dir)
    except:
        traceback.print_exc()
        exit_code = 1
    logger.info('Finalizing task execution...')
    logger.info(f'Exiting with {exit_code}...')
    sys.exit(exit_code)
except BaseException as e:
    if _extract_boolean_parameter('CP_CAP_ZOMBIE'):
        try:
            logger.error('Switching to zombie mode because the error occurred.', trace=True)
        except:
            traceback.print_exc()
            print('Switching to zombie mode because the error occurred...')
        import time

        while True:
            time.sleep(1)
    else:
        raise
