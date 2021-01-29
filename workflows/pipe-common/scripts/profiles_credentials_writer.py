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

from pipeline import PipelineAPI


def find_region(api, region_id):
    region = api.get_region(region_id)
    if not region:
        raise RuntimeError("Failed to find region by ID %s" % str(region_id))
    return region


def find_profiles_by_user(api, user_id):
    return api.load_profiles_for_user(user_id)


def filter_profiles_by_region(profiles, region):
    if region.get('mountCredentialsRule') == 'NONE':
        return []
    if region.get('mountCredentialsRule') == 'ALL':
        return profiles
    if region.get('mountCredentialsRule') == 'CLOUD':
        return [profile for profile in profiles if profile.get('cloudProvider') == region.get('provider')]


def build_command(path_to_script, profile_id, python_path):
    return "%s %s --profile-id=%s" % (python_path, path_to_script, profile_id)


def write_content(f, credentials_process, profile_name, region_field):
    f.write(profile_name)
    f.write("\n")
    f.write(region_field)
    f.write("\n")
    f.write(credentials_process)
    f.write("\n")


def write_to_config_file(profiles, region, path_to_script, path_to_config, python_path, default_profile_id=None):
    if not profiles:
        return
    with open(path_to_config, 'w+') as f:
        for profile in profiles:
            default_profile_name = '[default]' if default_profile_id \
                                                  and int(profile.get('id')) == int(default_profile_id) else None
            profile_name = '[profile %s]' % profile.get('profileName')
            credentials_process = 'credential_process = %s' % build_command(path_to_script, profile.get('id'),
                                                                            python_path)
            region_field = "region = %s" % region.get('regionId')
            if default_profile_name:
                write_content(f, credentials_process, default_profile_name, region_field)
                if profile.get('profileName') == 'default':
                    continue
            write_content(f, credentials_process, profile_name, region_field)


def find_user(api, user_name):
    user = api.load_user_by_name(user_name)
    if not user:
        raise RuntimeError("Failed to load user by name '%s'" % user_name)
    return user


def find_default_profile_id(user):
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--script-path', required=True)
    parser.add_argument('--python-path', required=True)  # $CP_PYTHON2_PATH
    parser.add_argument('--config-file', default="~/.aws/config")
    parser.add_argument('--log-dir', default='logs')
    args = parser.parse_args()

    script_path = args.script_path
    python_path = args.python_path
    config_file = os.path.expanduser(args.config_file)
    log_dir = args.log_dir

    api = PipelineAPI(os.environ['API'], log_dir)
    region_id = int(os.environ['CLOUD_REGION_ID'])
    user_name = str(os.environ['OWNER']).upper()

    region = find_region(api, region_id)
    user = find_user(api, user_name)
    user_id = int(user.get('id'))
    default_profile_id = find_default_profile_id(user)
    profiles = filter_profiles_by_region(find_profiles_by_user(api, user_id), region)

    write_to_config_file(profiles, region, script_path, config_file, python_path, default_profile_id)


if __name__ == '__main__':
    main()
