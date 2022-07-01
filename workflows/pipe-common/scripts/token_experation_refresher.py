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

import os
from time import sleep, time
from pipeline.api import PipelineAPI
import jwt

ENV_SRC = "/etc/cp_env.sh"
API_TOKEN_PREFIX = "export API_TOKEN="
MONITORING_DELAY = 1
GLOBAL_REFRESH_THRESHOLD = "launch.jwt.token.expiration.refresh.threshold"
API_URL = os.environ['API']
LOG_DIR = os.environ.get('LOG_DIR', 'logs')


def read_env_src():
    with open(ENV_SRC, 'r') as f:
        return f.readlines()


def get_token_from_env_src():
    lines = read_env_src()
    token = None
    for line in lines:
        if line.startswith(API_TOKEN_PREFIX):
            token = line[len(API_TOKEN_PREFIX):].strip()
    return token


def get_global_token_refresh_threshold(api):
    preference = api.get_preference(GLOBAL_REFRESH_THRESHOLD)
    if not preference or not preference.get('value'):
        return None
    return preference.get('value')


def load_contextual_preference(api, user_id):
    try:
        query = '/contextual/preference/load?name=%s&level=USER&resourceId=%d' % (GLOBAL_REFRESH_THRESHOLD, user_id)
        result = api.execute_request(str(API_URL) + query)
        return {} if result is None else result
    except BaseException as error:
        print("Failed to load user refresh threshold from contextual preferences. Error message: %s" % error.message)
        return None


def get_user_token_refresh_threshold(api, user_id):
    preference = load_contextual_preference(api, user_id)
    if not preference or not preference.get('value'):
        return None
    return preference.get('value')


def load_user_token(api, user_name, duration=None):
    try:
        query = '/user/token?name=%s' % user_name
        if duration:
            query = '&expiration='.join([query, str(duration)])
        result = api.execute_request(str(API_URL) + query)
        return {} if result is None else result
    except BaseException as e:
        raise RuntimeError("Failed to load user token. Error message: %s" % e.message)


def get_user_token(api, user_name, duration=None):
    token_response = load_user_token(api, user_name, duration)
    token = token_response.get('token', None)
    if not token:
        raise RuntimeError('User token is empty')
    return token


def get_refresh_threshold(api, user_id):
    refresh_threshold = get_user_token_refresh_threshold(api, user_id)
    if not refresh_threshold:
        refresh_threshold = get_global_token_refresh_threshold(api)
    if not refresh_threshold:
        raise RuntimeError("Token refresh threshold was not specified")
    return refresh_threshold


def token_refresh_required(api, user_id, token_expiration_date):
    refresh_threshold = get_refresh_threshold(api, user_id)
    current_time = time()
    return (int(token_expiration_date) - current_time) <= int(refresh_threshold)


def refresh_token():
    api_token = get_token_from_env_src()
    token_from_file = True
    if not api_token:
        token_from_file = False
        api_token = os.getenv("API_TOKEN")
    if not api_token:
        raise RuntimeError("API_TOKEN was not provided")
    token_payload = jwt.decode(api_token, verify=False)
    token_expiration_date = token_payload.get('exp', None)
    if not token_expiration_date:
        raise RuntimeError("Cannot determine expiration date for current token")

    api = PipelineAPI(API_URL, LOG_DIR)
    user = api.load_current_user()
    username = user.get("userName", None)
    user_id = user.get("id", None)

    if not token_refresh_required(api, user_id, token_expiration_date):
        print("No token refresh required")
        return

    new_token = get_user_token(api, username)
    if token_from_file:
        os.system("sed -i 's|API_TOKEN=.*|API_TOKEN=%s|g' %s" % (new_token, ENV_SRC))
    else:
        os.system('echo "export API_TOKEN=%s" >> %s' % (new_token, ENV_SRC))


if __name__ == '__main__':
    while True:
        try:
            refresh_token()
        except BaseException as e:
            print(e)
        delay = os.getenv("CP_JWT_TOKEN_EXPIRATION_CHECK_TIMEOUT_HOUR", MONITORING_DELAY)
        sleep(int(delay) * 60 * 60)
