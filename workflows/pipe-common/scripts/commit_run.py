# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import pipeline.api
import sys
import time
from pipeline import PipelineAPI


def get_group_info_by_id(tool_group_id, max_attempts=10):
    attempts = 0
    tool_group = None
    while attempts < max_attempts:
        attempts += 1
        try:
            tool_group = api.tool_group_load(tool_group_id)
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'.format(e=e, attempts=attempts, max_attempts=max_attempts)
    if tool_group is not None:
        return tool_group
    raise RuntimeError("Failed to load group info for: {}".format(tool_group))


def get_tool_from_run(api, run_id, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            run_info = api.load_run(run_id)
            return api.load_tool(image=run_info['dockerImage'], registry='')
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'.format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to fetch tool from run for run_id: {}.".format(run_id))


def update_commit_status(api, run_id, commit_status, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            return api.update_commit_status(run_id, commit_status)
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'.format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to update commit status for run_id: {}.".format(run_id))


def enable_tool(api, tool, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            return api.enable_tool(tool)
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'.format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to enable tool: {}/{}.".format(tool.registry, tool.image))


def update_tool(api, tool, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            return api.update_tool(tool)
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'.format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to update tool: {}/{}.".format(tool.registry, tool.image))


def stop_pipeline(api, run_id, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            return api.update_status(run_id, pipeline.api.StatusEntry(pipeline.api.TaskStatus.STOPPED))
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'.format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to stop_pipeline with id: {}".format(run_id))


def tool_exist(group_id, new_image_name):
    group_info = get_group_info_by_id(group_id)
    if 'tools' in group_info:
        for tool in group_info['tools']:
            if tool['image'] == new_image_name:
                return True
    return False

def tool_version_exist(new_image_name, version, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            new_tool = api.load_tool(image=new_image_name, registry='')
            if new_tool and new_tool.tool_id == 0:
                raise RuntimeError('Tool {new_image_name} returned with id exiting'.format(new_image_name))

            tool_versions_list = api.load_tool_versions(new_tool.tool_id)
            return True if tool_versions_list and version in tool_versions_list else False
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'.format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to invoke tool_version_exist for tool: {}".format(new_image_name))


def get_tool_version_settings(tool_id, version, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            return api.load_tool_version_settings(tool_id, version)
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'\
                .format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to invoke get_tool_version_settings for tool: {}".format(tool_id))


def create_settings_for_tool_version(tool_id, version, settings, max_attempts=10):
    if not settings:
        return None
    if 'settings' not in settings[0]:
        return None
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            return api.create_setting_for_tool_version(tool_id, version, settings[0]['settings'])
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})' \
                .format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to invoke create_settings_for_tool_version")


def load_tool(image, max_attempts=10):
    attempts = 0
    while attempts < max_attempts:
        attempts += 1
        try:
            return api.load_tool(image=image, registry='')
        except Exception as e:
            print 'There is exception with message:{e} \n Retry: ({attempts}/{max_attempts})'\
                .format(e=e, attempts=attempts, max_attempts=max_attempts)
    raise RuntimeError("Failed to invoke load_tool.")


def get_tool_id(image, max_attempts=10):
    tool = load_tool(image, max_attempts)
    if not tool or tool.tool_id == 0:
        raise RuntimeError("Tool loaded by name %s is invalid" % image)
    return tool.tool_id


def parse_image(image):
    splitted_image = image.split(':')
    image_name = splitted_image[0]
    image_tag = None if len(splitted_image) == 1 else splitted_image[1]
    return image_name, image_tag


def get_image_name_and_tag(image_name_with_tag):
    image_name, image_tag = parse_image(image_name_with_tag)
    if image_tag is None:
        image_tag = 'latest'
    return image_name, image_tag


def add_settings(new_tool_id, new_version, initial_tool_id, initial_version):
    settings = get_tool_version_settings(initial_tool_id, initial_version)
    create_settings_for_tool_version(new_tool_id, new_version, settings)


if __name__ == '__main__':
    api = PipelineAPI(os.environ['API'], 'logs')
    command = sys.argv[1]
    run_id = sys.argv[2]

    if command == "ups":
        status_to_update = None
        new_status = sys.argv[3]
        if new_status == "FAILURE":
            status_to_update = pipeline.api.CommmitStatus.FAILURE
        elif new_status == "SUCCESS":
            status_to_update = pipeline.api.CommmitStatus.SUCCESS
        else:
            raise RuntimeError("Wrong argument for update_commit_status: {}".format(new_status))
        update_commit_status(api, run_id, status_to_update)

    elif command == "ite":
        tool_group_id = int(sys.argv[3])
        #remove tag if it exists <group>/<image name>:<tag>
        new_image_name =  str.split(sys.argv[4], ':')[0]
        print tool_exist(tool_group_id, new_image_name)

    elif command == "ive":
        new_tool_name = sys.argv[3]
        version_to_check = sys.argv[4]
        print tool_version_exist(new_tool_name, version_to_check)

    elif command == "etwc":

        new_registry = sys.argv[3]
        registry_id = sys.argv[4]
        tool_group_id = int(sys.argv[5])

        new_image_name, new_image_tag = parse_image(sys.argv[6])

        retry_time = int(sys.argv[7])
        backoff_time = 5
        retry_count = retry_time / backoff_time

        template = get_tool_from_run(api, run_id)
        template.registry = new_registry
        template.registryId = registry_id
        template.toolGroupId = tool_group_id
        template_image_name, template_image_tag = parse_image(template.image)
        template.image = new_image_name
        tool_id = template.tool_id

        if os.getenv('IS_PIPELINE_AUTH', 'false') == 'true':
            attempts = 0
            while attempts < retry_count:
                attempts += 1
                if tool_exist(tool_group_id, new_image_name):
                    update_tool(api, template)
                    sys.exit(0)
                else:
                    time.sleep(backoff_time)
        enable_tool(api, template)
    # enable version settings
    elif command == "evs":
        new_registry = sys.argv[3]
        new_image = sys.argv[4]

        new_tool = api.load_tool(new_image, new_registry)
        new_image_name, new_image_tag = get_image_name_and_tag(new_image)

        initial_tool = get_tool_from_run(api, run_id)
        initial_image_name, initial_image_tag = get_image_name_and_tag(initial_tool.image)
        # case when commit in the same tool and version
        if new_tool.tool_id == initial_tool.tool_id and \
                new_image_name == initial_image_name and \
                new_image_tag == initial_image_tag:
            sys.exit(0)
        add_settings(new_tool.tool_id, new_image_tag, initial_tool.tool_id, initial_image_tag)

    elif command == "sp":
        stop_pipeline(api, run_id)

