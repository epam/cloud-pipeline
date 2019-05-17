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

from cmd_utils import *


def pipe_storage_cp(source, destination, force=False, recursive=False, include=[], exclude=[], args=None,
                    expected_status=0, token=None, tags=[], skip_existing=False):
    command = ['pipe', 'storage', 'cp', source, destination]
    if force:
        command.append('--force')
    if recursive:
        command.append('--recursive')
    for pattern in include:
        command.append('--include')
        command.append(pattern)
    for pattern in exclude:
        command.append('--exclude')
        command.append(pattern)
    for tag in tags:
        command.append('--tags')
        command.append('{}={}'.format(tag[0], tag[1]))
    if skip_existing:
        command.append('-s')
    return get_command_output(command, args=args, expected_status=expected_status, token=token)


def pipe_storage_mv(source, destination, force=False, recursive=False, include=[], exclude=[], args=None,
                    expected_status=0, token=None, skip_existing=False):
    command = ['pipe', 'storage', 'mv', source, destination]
    if force:
        command.append('--force')
    if recursive:
        command.append('--recursive')
    for pattern in include:
        command.append('--include')
        command.append(pattern)
    for pattern in exclude:
        command.append('--exclude')
        command.append(pattern)
    if skip_existing:
        command.append('-s')
    return get_command_output(command, args=args, expected_status=expected_status, token=token)


def pipe_storage_ls(source, recursive=False, show_details=True, args=None, expected_status=0, token=None,
                    versioning=False, paging=None):
    command = ['pipe', 'storage', 'ls']
    if source:
        command.append(source)
    if show_details:
        command.append("--show_details")
    if recursive:
        command.append("--recursive")
    if versioning:
        command.append("-v")
    if paging:
        command.append("-p")
        command.append(paging)
    return get_command_output(command, args=args, expected_status=expected_status, token=token)


def pipe_storage_rm(source, recursive=False, args=None, expected_status=0, token=None):
    command = ['pipe', 'storage', 'rm', source, '--yes']
    if recursive:
        command.append('--recursive')
    return get_command_output(command, args=args, expected_status=expected_status, token=token)


def create_data_storage(bucket_name, path=None, versioning=False, token=None, expected_status=0,
                        folder=None, sts="", lts="", backup_duration=""):
    provider = os.environ['CP_PROVIDER']
    region_id = os.environ['CP_TEST_REGION_ID']
    path = path if path else bucket_name
    command = ['pipe', 'storage', 'create', '--name', bucket_name, '-c', '--path', path,
               "-d", "The test bucket for integration testing", "-sts", sts, "-lts", lts, "-t", provider,
               "-b", backup_duration, "-r", region_id]
    if folder:
        command.extend(["-f", str(folder)])
    else:
        command.extend(["-f", ""])
    if versioning:
        command.append('-v')
    get_command_output(command, expected_status=expected_status, token=token)


def pipe_storage_restore(path, version=None, expected_status=None, token=None):
    command = ['pipe', 'storage', 'restore', path]
    if version:
        command.append('-v')
        command.append(version)
    return get_command_output(command, expected_status=expected_status, token=token)


def delete_data_storage(bucket_name):
    command = ['pipe', 'storage', 'delete', '--name', bucket_name, '-c', '-y']
    subprocess.Popen(command, stdout=subprocess.PIPE).wait()


def set_acl_permissions(user, identifier, acl_class, allow=None, deny=None):
    command = ['pipe', 'set-acl', identifier, '-t', acl_class, '-s', user]
    if allow is not None:
        command.extend(['-a', allow])
    if deny is not None:
        command.extend(['-d', deny])
    return subprocess.Popen(command, stdout=subprocess.PIPE).wait()


def set_storage_permission(user, bucket, allow=None, deny=None):
    exit_code = set_acl_permissions(user, bucket, 'data_storage', allow=allow, deny=deny)
    assert exit_code == 0, "Failed to set permissions for bucket {}".format(bucket)


def pipe_tag_set(entity_class, entity_identifier, args=list(), expected_status=None, token=None):
    command = ['pipe', 'tag', 'set', entity_class, entity_identifier]
    return get_command_output(command, args=args, expected_status=expected_status, token=token)


def pipe_tag_get(entity_class, entity_identifier, expected_status=None, token=None):
    command = ['pipe', 'tag', 'get', entity_class, entity_identifier]
    return get_command_output(command, expected_status=expected_status, token=token)


def pipe_tag_delete(entity_class, entity_identifier, args=list(), expected_status=None, token=None):
    command = ['pipe', 'tag', 'delete', entity_class, entity_identifier]
    return get_command_output(command, args=args, expected_status=expected_status, token=token)


def pipe_storage_mkdir(destinations, expected_status=None, token=None):
    command = ['pipe', 'storage', 'mkdir']
    for path in destinations:
        command.append(path)
    return get_command_output(command, expected_status=expected_status, token=token)


def pipe_storage_mvtodir(storage_name, directory="/", expected_status=None, token=None):
    command = ['pipe', 'storage', 'mvtodir', storage_name, directory]
    return get_command_output(command, expected_status=expected_status, token=token)


def get_storage_tags(path, version=None, expected_status=0, token=None):
    command = ['pipe', 'storage', 'get-object-tags', path]
    if version:
        command.extend(['-v', version])
    return get_command_output(command, expected_status=expected_status, token=token)


def set_storage_tags(path, tags, args=None, version=None, expected_status=0, token=None):
    command = ['pipe', 'storage', 'set-object-tags', path]
    for tag in tags:
        command.append('{}={}'.format(tag[0], tag[1]))
    if version:
        command.extend(['-v', version])
    return get_command_output(command, args=args, expected_status=expected_status, token=token)


def delete_storage_tags(path, tags, version=None, expected_status=0, token=None):
    command = ['pipe', 'storage', 'delete-object-tags', path]
    command.extend(tags)
    if version:
        command.extend(['-v', version])
    return get_command_output(command, expected_status=expected_status, token=token)


def pipe_storage_rm_piped(source, delete=True):
    pipe_command = ['pipe', 'storage', 'rm', source]
    if delete:
        echo_command = ['echo', 'y']
    else:
        echo_command = ['echo', 'N']
    echo_process = subprocess.Popen(echo_command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    pipe_process = subprocess.Popen(pipe_command, stdout=subprocess.PIPE, stdin=echo_process.stdout,
                                    stderr=subprocess.PIPE)
    echo_process.stdout.close()
    process = pipe_process.communicate()
    stdout = process[0]
    stderr = process[1]
    return stdout, stderr


def pipe_storage_policy(bucket_name, sts="", lts="", backup_duration=""):
    process = subprocess.Popen(["pipe", "storage", "policy", "-n", bucket_name, "-v"],
                               stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE)
    process.stdin.write("{}\n".format(sts))
    process.stdin.write("{}\n".format(lts))
    process.stdin.write("{}\n".format(backup_duration))
    process.stdin.close()
    process.wait()
    return process.stdout, process.stderr
