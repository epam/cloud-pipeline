import os
import requests
import signal
import socket
import subprocess
import time
import json
from flask import Flask, Response, request


app = Flask(__name__)
SHINY_VALIDATOR_CHECK_NUM = int(os.getenv('SHINY_APP_VALIDATOR_BOOT_TIMEOUT_NUM', 10))
SHINY_VALIDATOR_CHECK_DELAY = float(os.getenv('SHINY_APP_VALIDATOR_BOOT_TIMEOUT_SEC', 3))
SHINY_VALIDATOR_PORT = os.getenv('SHINY_APP_VALIDATOR_PORT', 3838)
VALID_PING_RESPONSE_CODES = map(int, os.getenv('SHINY_APP_VALID_PING_RESPONSE_CODES', '200').split(','))


def _validation_info_response(target_folder, exit_code, message):
    return Response(json.dumps({
        "app": target_folder,
        "is_valid": exit_code == 0,
        "message": message
    }))


def _get_free_port():
    s = socket.socket()
    s.bind(("", 0))
    free_port = s.getsockname()[1]
    s.close()
    return free_port


def _extract_error(shiny_app_descriptor):
    _, stderr = shiny_app_descriptor.communicate()
    return stderr.strip()


def _kill_process(pid):
    os.kill(int(pid), signal.SIGTERM)


def _ping_local_shiny_app(port):
    try:
        return requests.get(url='http://localhost:{}'.format(port))
    except Exception as e:
        return None


@app.route('/<path:prefix>')
def validate_app(prefix):
    target_folder = request.args['target_folder']
    if not os.path.exists(target_folder) or not os.path.isdir(target_folder):
        no_target_path_message = 'No such directory [{}] exists'.format(target_folder)
        print(no_target_path_message)
        return _validation_info_response(target_folder, 2, no_target_path_message)
    target_port = _get_free_port()
    print('Target port: {}'.format(target_port))
    shiny_app = subprocess.Popen(['R', '-e', 'shiny::runApp(\'{}\', port={})'.format(target_folder, target_port)],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=target_folder)
    shiny_app_pid = shiny_app.pid
    print('Spawned Shiny app PID: {}'.format(shiny_app_pid))
    time.sleep(SHINY_VALIDATOR_CHECK_DELAY)
    listen_iter = 0
    listening_pid = None
    while listen_iter < SHINY_VALIDATOR_CHECK_NUM:
        listening_pid_search = subprocess.Popen(['lsof', '-ti:{}'.format(target_port)], stdout=subprocess.PIPE)
        listening_pid = listening_pid_search.stdout.read().strip('\n')
        if listening_pid:
            break
        print('No process listening on port [{}] found, will retry in {} sec'.format(target_port, SHINY_VALIDATOR_CHECK_DELAY))
        time.sleep(SHINY_VALIDATOR_CHECK_DELAY)
        listen_iter += 1

    if not listening_pid:
        print('No process listening on port [{}] found, stopping checks'.format(target_port))
        error_message = _extract_error(shiny_app)
        return _validation_info_response(target_folder, 1, error_message)
    elif int(listening_pid) != shiny_app_pid:
        print('PID={} is listening on port {}, but desired PID is {}'.format(listening_pid, target_port, shiny_app_pid))
        error_message = _extract_error(shiny_app)
        return _validation_info_response(target_folder, 1, error_message)
    print('Shiny app was started on port [{}], starting "ping"'.format(target_port))
    response = _ping_local_shiny_app(target_port)
    print('"ping" is done, killing Shiny app process')
    _kill_process(listening_pid)
    if response is None:
        error_message = 'Unable to ping active Shiny app.'
        print(error_message)
        validation_response = _validation_info_response(target_folder, 1, error_message)
    elif response.status_code not in VALID_PING_RESPONSE_CODES:
        error_message = 'Application was able to start, but ping failed with status code `{}`'\
            .format(response.status_code)
        print(error_message)
        error_message += '\n{}'.format(_extract_error(shiny_app))
        validation_response = _validation_info_response(target_folder, 1, error_message)
    else:
        print('Application was able to start')
        validation_response = \
            _validation_info_response(target_folder, 0,
                                      'Validation is successful, ping returned status [{}].'.format(response.status_code))
    return validation_response


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=SHINY_VALIDATOR_PORT)

