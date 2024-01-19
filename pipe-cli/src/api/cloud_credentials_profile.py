# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

from src.api.base import API

class CloudCredentialsProfile(API):

    def __init__(self):
        super(CloudCredentialsProfile, self).__init__()

    @classmethod
    def find_profiles_by_user(cls, user_id):
        api = cls.instance()
        response_data = api.call('cloud/credentials?userId={}'.format(user_id), None)
        if 'payload' in response_data:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to load user profiles.")

    @classmethod
    def generate_credentials(cls, profile_id):
        api = cls.instance()
        response_data = api.call('cloud/credentials/generate/{}'.format(profile_id), None)
        if 'payload' in response_data:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to generate credentials for profile {}".format(profile_id))
        

