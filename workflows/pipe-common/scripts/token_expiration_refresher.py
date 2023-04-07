# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

from datetime import datetime
import os
from time import sleep, time
from pipeline.api import PipelineAPI
import jwt

ENV_SRC = os.environ.get('CP_ENV_FILE_TO_SOURCE', '/etc/cp_env.sh')
API_TOKEN_PREFIX = "export API_TOKEN="
MONITORING_DELAY_HOUR = int(os.getenv("CP_JWT_TOKEN_EXPIRATION_CHECK_TIMEOUT_HOUR", 1)) * 60 * 60
GLOBAL_REFRESH_THRESHOLD = "launch.jwt.token.expiration.refresh.threshold"
API_URL = os.environ['API']
LOG_DIR = os.environ.get('LOG_DIR', 'logs')


def read_env_src():
    if not os.path.isfile(ENV_SRC):
        return []
    with open(ENV_SRC, 'r') as f:
        return f.readlines()


def get_token_from_env_src():
    lines = read_env_src()
    token = None
    for line in lines:
        if line.startswith(API_TOKEN_PREFIX):
            token = line[len(API_TOKEN_PREFIX):].strip().strip('"')
    return token


def get_global_token_refresh_threshold(api):
    preference = api.get_preference(GLOBAL_REFRESH_THRESHOLD)
    if not preference or not preference.get('value'):
        return None
    return preference.get('value')


def get_user_token_refresh_threshold(api):
    preference = api.search_contextual_preference(GLOBAL_REFRESH_THRESHOLD)
    if not preference or not preference.get('value'):
        return None
    return preference.get('value')


def get_user_token(api, user_name, duration=None):
    token_response = api.generate_user_token(user_name, duration)
    token = token_response.get('token', None)
    if not token:
        raise RuntimeError('[ERROR] User token is empty')
    return token


def get_refresh_threshold(api):
    refresh_threshold = get_user_token_refresh_threshold(api)
    if not refresh_threshold:
        refresh_threshold = get_global_token_refresh_threshold(api)
    if not refresh_threshold:
        raise RuntimeError("[INFO] Token refresh threshold was not specified")
    return refresh_threshold


def token_refresh_required(api, token_expiration_date):
    refresh_threshold = get_refresh_threshold(api)
    current_time = time()
    return (int(token_expiration_date) - current_time) <= int(refresh_threshold)


def refresh_token():
    api_token = get_token_from_env_src()
    token_from_file = True
    if not api_token:
        token_from_file = False
        api_token = os.getenv("API_TOKEN")
    if not api_token:
        raise RuntimeError("[ERROR] API_TOKEN was not provided")
    token_payload = jwt.decode(api_token, verify=False)
    token_expiration_date = token_payload.get('exp', None)
    if not token_expiration_date:
        raise RuntimeError("[ERROR] Cannot determine expiration date for current token")

    api = PipelineAPI(API_URL, LOG_DIR)
    user = api.load_current_user()
    username = user.get("userName", None)

    if not token_refresh_required(api, token_expiration_date):
        # No token refresh required
        return

    new_token = get_user_token(api, username)
    if token_from_file:
        os.system('sed -i \'s|API_TOKEN=.*|API_TOKEN="%s"|g\' %s' % (new_token, ENV_SRC))
    else:
        os.system('echo \'export API_TOKEN=\"%s\"\' >> %s' % (new_token, ENV_SRC))
    print("[INFO] Token has been refreshed on {}".format(datetime.now()))


if __name__ == '__main__':
    while True:
        try:
            refresh_token()
        except BaseException as e:
            print(e)
        sleep(MONITORING_DELAY_HOUR)
