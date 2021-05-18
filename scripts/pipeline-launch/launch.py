import datetime
import json
import logging
import os
import pathlib
import subprocess
import traceback
import zipfile

import sys
import tarfile
import time


class LaunchError(RuntimeError):
    pass


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


class CloudPipelineAPI:

    def __init__(self, api_url, api_token, attempts=3, timeout=5, connection_timeout=10):
        self._api_url = (api_url or '').strip('/')
        self._api_token = api_token
        self._attempts = attempts
        self._timeout = timeout
        self._connection_timeout = connection_timeout
        self._headers = {'Content-Type': 'application/json',
                         'Authorization': 'Bearer ' + self._api_token}

    def load_run(self, run_id):
        return self._request('GET', f'run/{run_id}') or {}

    def log(self, run_id, message, task, status, date):
        self._request('POST', f'run/{run_id}/log', data={
            'runId': run_id,
            'logText': message,
            'taskName': task,
            'status': status,
            'date': date
        })

    def _request(self, http_method, endpoint, data=None):
        url = '{}/{}'.format(self._api_url, endpoint)
        count = 0
        exceptions = []
        while count < self._attempts:
            count += 1
            try:
                response = requests.request(method=http_method, url=url, data=json.dumps(data),
                                            headers=self._headers, verify=False,
                                            timeout=self._connection_timeout)
                if response.status_code != 200:
                    raise HTTPError('API responded with http status %s.' % str(response.status_code))
                response_data = response.json()
                status = response_data.get('status') or 'ERROR'
                message = response_data.get('message') or 'No message'
                if status != 'OK':
                    raise APIError('%s: %s' % (status, message))
                return response_data.get('payload')
            except APIError as e:
                raise e
            except Exception as e:
                exceptions.append(e)
            time.sleep(self._timeout)
        raise exceptions[-1]


class CloudPipelineLogger:

    _DATE_FORMAT = '%Y-%m-%d %H:%M:%S.%f'

    def __init__(self, api):
        self._api = api
        self._run_id = run_id

    def info(self, message, task):
        self._log(message=message, task=task, status='RUNNING')

    def warn(self, message, task):
        self._log(message=message, task=task, status='RUNNING')

    def success(self, message, task):
        self._log(message=message, task=task, status='SUCCESS')

    def error(self, message, task):
        self._log(message=message, task=task, status='FAILURE')

    def _log(self, message, task, status):
        self._api.log(run_id=self._run_id, message=message, task=task, status=status,
                      date=datetime.datetime.utcnow().strftime(self._DATE_FORMAT))


def _mkdir(path):
    pathlib.Path(path).mkdir(parents=True, exist_ok=True)


def _install_python_package(*packages):
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', '-q'] + list(packages))


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


class RemoteHostSSH:

    def __init__(self, host, private_key_path):
        self._host = host
        self._private_key_path = private_key_path

    def execute(self, command, user):
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.MissingHostKeyPolicy())
        client.connect(self._host, username=user, key_filename=self._private_key_path)
        _, stdout, stderr = client.exec_command(command)
        exit_code = stdout.channel.recv_exit_status()
        for line in stdout:
            logging.info(line.strip('\n'))
        for line in stderr:
            logging.warning(line.strip('\n'))
        if exit_code != 0:
            raise LaunchError(f'SSH command has finished with {exit_code} exit code on the remote host.')
        client.close()


if __name__ == '__main__':
    logging_format = os.environ['CP_LOGGING_FORMAT'] = os.getenv('CP_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')
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
    distribution_url = os.environ['DISTRIBUTION_URL'] = os.getenv('DISTRIBUTION_URL')
    api_url = os.environ['API'] = os.getenv('API')
    api_token = os.environ['API_TOKEN'] = os.getenv('API_TOKEN')
    node_owner = os.environ['CP_NODE_OWNER'] = os.getenv('CP_NODE_OWNER', 'ROOT')
    node_private_key_path = os.environ['CP_NODE_PRIVATE_KEY'] = os.getenv('CP_NODE_PRIVATE_KEY', os.path.join(host_root, '.ssh', 'id_rsa'))
    owner = os.environ['OWNER'] = os.getenv('OWNER')
    owner_password = os.environ['OWNER_PASSWORD'] = os.getenv('OWNER_PASSWORD', os.getenv('SSH_PASS'))
    task_path = os.environ['CP_TASK_PATH']
    python_dir = os.environ['CP_PYTHON_DIR'] = os.environ.get('CP_PYTHON_DIR', 'c:\\python')

    api = CloudPipelineAPI(api_url=api_url, api_token=api_token)
    api_logger = CloudPipelineLogger(api=api)

    logging.basicConfig(level=logging_level, format=logging_format)

    logging.info('Creating system directories...')
    _mkdir(run_id)
    _mkdir(common_repo_dir)
    _mkdir(log_dir)
    _mkdir(pipe_dir)
    _mkdir(analysis_dir)

    logging.info('Installing python packages...')
    _install_python_package('requests==2.25.1', 'paramiko==2.7.2')
    import urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    import requests
    import paramiko
    logging.getLogger('paramiko').setLevel(logging.WARNING)

    logging.info('Downloading pipe common...')
    _download_file(distribution_url + 'pipe-common.tar.gz', os.path.join(common_repo_dir, 'pipe-common.tar.gz'))

    logging.info('Unpacking pipe common...')
    _extract_archive(os.path.join(common_repo_dir, 'pipe-common.tar.gz'), common_repo_dir)

    logging.info('Installing pipe common...')
    _install_python_package(common_repo_dir)
    from scripts import add_to_path

    logging.info('Configuring PATH...')
    add_to_path(os.path.join(common_repo_dir, 'powershell'))
    add_to_path(pipe_dir)

    logging.info('Downloading pipe...')
    _download_file(distribution_url + 'pipe.zip', os.path.join(pipe_dir, 'pipe.zip'))

    logging.info('Unpacking pipe...')
    _extract_archive(os.path.join(pipe_dir, 'pipe.zip'), os.path.dirname(pipe_dir))

    logging.info('Configuring pipe...')
    subprocess.check_call(f'powershell -Command "pipe.exe configure --api \'{api_url}\' --auth-token \'{api_token}\' --timezone local --proxy pac"')

    logging.info('Preparing for SSH connections to the node...')
    run = api.load_run(run_id)
    node_ip = os.environ['NODE_IP'] = run.get('instance', {}).get('nodeIP', '')
    node_ssh = RemoteHostSSH(host=node_ip, private_key_path=node_private_key_path)

    logging.info('Installing pipe common on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -m pip install -q {common_repo_dir}',
                     user=node_owner)

    logging.info('Configuring PATH on the node...')
    node_ssh.execute(f'{python_dir}\\python.exe -c \\"from scripts import add_to_path; '
                     f'add_to_path(\'{_escape_backslashes(python_dir)}\'); '
                     f'add_to_path(\'{_escape_backslashes(os.path.join(common_repo_dir, "powershell"))}\'); '
                     f'add_to_path(\'{_escape_backslashes(pipe_dir)}\')\\"',
                     user=node_owner)

    logging.info('Configuring owner account on the node...')
    node_ssh.execute(f'AddUser -UserName {owner} -UserPassword {owner_password}',
                     user=node_owner)

    logging.info('Configuring pipe on the node...')
    node_ssh.execute(f'pipe configure --api \'{api_url}\' --auth-token \'{api_token}\' --timezone local --proxy pac',
                     user=node_owner)
    node_ssh.execute(f'pipe configure --api \'{api_url}\' --auth-token \'{api_token}\' --timezone local --proxy pac',
                     user=owner)

    logging.info('Configuring node SSH server proxy...')
    subprocess.check_call(f'powershell -Command "pipe.exe tunnel start --direct -lp 22 -rp 22 --trace '
                          f'-l {_escape_backslashes(os.path.join(run_dir, "ssh_proxy.log"))} '
                          f'{node_ip}"')

    api_logger.success('Environment initialization finished', task='InitializeEnvironment')

    logging.info('Executing task...')
    task_wrapping_command = f'powershell -Command ". {host_root}\\NodeEnv.ps1; & {task_path} -ErrorAction Stop"'
    logging.info(f'Task command: {task_wrapping_command}')
    try:
        exit_code = subprocess.call(task_wrapping_command, cwd=analysis_dir)
    except:
        traceback.print_exc()
        exit_code = 1
    logging.info('Finalizing task execution...')
    logging.info(f'Exiting with {exit_code}...')
    sys.exit(exit_code)
