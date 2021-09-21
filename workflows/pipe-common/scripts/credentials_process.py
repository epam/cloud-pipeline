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
import argparse
import os

from dateutil.parser import parse
from pipeline import PipelineAPI

CONTENT_FORMAT = """{
    "Version": 1,
    "AccessKeyId": "%s",
    "SecretAccessKey": "%s",
    "SessionToken": "%s",
    "Expiration" : "%s"
}"""
DATE_TIME_SERVER_FORMAT = '%Y-%m-%d %H:%M:%S Z'
DATE_TIME_CREDENTIALS_FORMAT = '%Y-%m-%dT%H:%M:%SZ'


def find_credentials(api, profile_id, region_id):
    credentials = api.load_profile_credentials(profile_id, region_id)
    if not credentials:
        raise RuntimeError("Failed to find credentials")
    return credentials


def covert_time(time_string):
    return parse(time_string).strftime(DATE_TIME_CREDENTIALS_FORMAT)


def print_credentials(api, profile_id, region_id):
    credentials = find_credentials(api, profile_id, region_id)

    print(CONTENT_FORMAT % (
        credentials.get('keyID'),
        credentials.get('accessKey'),
        credentials.get('token'),
        covert_time(credentials.get('expiration'))
    ))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--profile-id', required=True)
    parser.add_argument('--region-id', required=False, type=int)
    parser.add_argument('--log-dir', default='logs')
    args = parser.parse_args()

    profile_id = args.profile_id
    region_id = args.region_id
    log_dir = args.log_dir

    api = PipelineAPI(os.environ['API'], log_dir)

    print_credentials(api, profile_id, region_id)


if __name__ == '__main__':
    main()
