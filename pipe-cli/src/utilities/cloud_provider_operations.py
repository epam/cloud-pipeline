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

import os
import sys
import dateutil

from src.config import is_frozen
from src.utilities.user_operations_manager import UserOperationsManager
from src.api.cloud_credentials_profile import CloudCredentialsProfile
from src.api.cloud_region import CloudRegion

CONTENT_FORMAT = """{
    "Version": 1,
    "AccessKeyId": "%s",
    "SecretAccessKey": "%s",
    "SessionToken": "%s",
    "Expiration" : "%s"
}"""
DATE_TIME_SERVER_FORMAT = '%Y-%m-%d %H:%M:%S Z'
DATE_TIME_CREDENTIALS_FORMAT = '%Y-%m-%dT%H:%M:%SZ'

class CloudProviderOperations(object):
    @classmethod
    def config_exists(cls, provider, config_file=None):
        # TODO: To be extended for other providers
        if provider != 'aws':
            print('Only "aws" Cloud Provider is supported')
            return
        config_file = CloudProviderOperations.get_config_file_path(provider, config_file)
        return os.path.exists(config_file)

    @classmethod
    def configure(cls, provider, config_file=None, default_profile=None):
        # TODO: To be extended for other providers
        if provider != 'aws':
            print('Only "aws" Cloud Provider is supported')
            return

        config_file = CloudProviderOperations.get_config_file_path(provider, config_file)

        user_manager = UserOperationsManager()
        current_user = user_manager.whoami()

        default_profile_id = cls.find_default_profile_id(current_user)
        if default_profile_id:
            print("Got default profile id from a user account: {}".format(default_profile_id))

        all_profiles = CloudCredentialsProfile.find_profiles_by_user(current_user.get('id'))
        if default_profile:
            for profile in all_profiles:
                profile_id = profile.get('id')
                if str(profile.get('profileName')) == str(default_profile):
                    default_profile_id = profile_id
                    print("Got default profile id forced by the command line ({}): {}".format(default_profile, default_profile_id))

        if len(all_profiles) > 0:
            print("Got {} profiles for a curent user".format(len(all_profiles)))
        else:
            print("No cloud profiles are available for a current user")
            return

        user_profiles = {}
        for cloud_region in CloudRegion.get_cloud_regions():
            filtered_profiles = cls.filter_profiles_by_region(all_profiles, cloud_region)
            if len(filtered_profiles) > 0:
                user_profiles[cloud_region['regionId']] = filtered_profiles
        if len(user_profiles.keys()) > 0:
            print("{} profiles are left after filtering by the Cloud Region settings".format(len(user_profiles.keys())))
        else:
            print("No cloud profiles are left after filtering by the Cloud Region settings")
            return

        print("Writing configuration to {}".format(config_file))
        CloudProviderOperations.write_to_config_file(user_profiles, config_file, default_profile_id)

    @classmethod
    def generate_credentials(cls, profile_id):
        credentials = CloudCredentialsProfile.generate_credentials(profile_id)
        print(CONTENT_FORMAT % (
            credentials.get('keyID'),
            credentials.get('accessKey'),
            credentials.get('token'),
            CloudProviderOperations.covert_time(credentials.get('expiration'))
        ))


    def covert_time(time_string):
        return dateutil.parser.parse(time_string).strftime(DATE_TIME_CREDENTIALS_FORMAT)


    @classmethod
    def get_config_file_path(cls, provider, config_file):
        if not config_file:
            # TODO: To be extended for other providers
            config_file = "~/.aws/config"
        return os.path.expanduser(config_file)

    @classmethod
    def write_to_config_file(cls, profiles, config_file, default_profile_id=None):
        if not profiles:
            return

        CloudProviderOperations.create_config_dir(config_file)

        for region_id in profiles.keys():
            with open(config_file, 'w+') as f:
                for profile in profiles[region_id]:
                    default_profile_name = '[default]' if default_profile_id \
                                                        and int(profile.get('id')) == int(default_profile_id) else None
                    profile_name = '[profile %s]' % profile.get('profileName')
                    credentials_process = 'credential_process = %s' % CloudProviderOperations.build_command(profile.get('id'))
                    region_field = "region = " + str(region_id)
                    if default_profile_name:
                        CloudProviderOperations.write_content(f, credentials_process, default_profile_name, region_field)
                        if profile.get('profileName') == 'default':
                            continue
                    CloudProviderOperations.write_content(f, credentials_process, profile_name, region_field)

    @classmethod
    def build_command(cls, profile_id):
        executable_path = sys.argv[0] if is_frozen() else (sys.executable + ' ' + sys.argv[0])
        return '{} cloud print-credentials --profile-id={}'.format(executable_path, profile_id)

    @classmethod
    def write_content(cls, f, credentials_process, profile_name, region_field):
        f.write(profile_name)
        f.write("\n")
        f.write(region_field)
        f.write("\n")
        f.write(credentials_process)
        f.write("\n")

    @classmethod
    def create_config_dir(cls, path_to_config):
        if os.path.exists(path_to_config):
            os.remove(path_to_config)
        path_to_config_dir = os.path.dirname(path_to_config)
        if path_to_config_dir and not os.path.exists(path_to_config_dir):
            os.makedirs(path_to_config_dir)

    @classmethod
    def find_default_profile_id(cls, user):
        user_profile_id = user.get('defaultProfileId', None)
        if user_profile_id:
            return user_profile_id
        roles = user.get('roles')
        if not roles:
            return None
        for role in roles:
            role_profile_id = role.get('defaultProfileId', None)
            if role_profile_id:
                return role_profile_id
        return None

    @classmethod
    def filter_profiles_by_region(cls, profiles, region):
        if region.get('mountCredentialsRule') == 'NONE':
            return []
        if region.get('mountCredentialsRule') == 'ALL':
            return profiles
        if region.get('mountCredentialsRule') == 'CLOUD':
            return [profile for profile in profiles if profile.get('cloudProvider') == region.get('provider')]
