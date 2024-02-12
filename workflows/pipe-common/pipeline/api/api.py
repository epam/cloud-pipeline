# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import datetime
import fnmatch
import json
import os
import requests
import sys
import time
import urllib3

from .region import CloudRegion
from .datastorage import DataStorage
from .datastorage import DataStorageWithShareMount

# Date format expected by Pipeline API
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
# date format for filename generation
FILE_DATE_FORMAT = "%Y%m%d"


class LaunchError(RuntimeError):
    pass


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


class Tool:
    def __init__(self, image, cpu, ram, registry, registryId, toolGroupId):
        self.image = image
        """
         Task requirements to CPU resources. The CPU resource is measured in cpus.
         Fractional values are allowed. You can use the suffix m to mean mili.
         For example 100m cpu is 100 milicpu, and is the same as 0.1 cpu.
         For example, '500m' - means task requires half of CPU available.
        """
        self.cpu = cpu
        """
         Task requirements to RAM resources. The RAM resource is measured in bytes.
         You can express RAM as a plain integer or a fixed-point integer with one of
         these suffixes: E, P, T, G, M, K, Ei, Pi, Ti, Gi, Mi, Ki.
         For example, '6Gi' -  means task requires 6Gb of available RAM.
         """
        self.ram = ram
        self.registry = registry
        self.labels = []
        self.endpoints = []
        self.registryId = registryId
        self.toolGroupId = toolGroupId
        self.description = ''
        self.shortDescription = ''
        self.defaultCommand = ''
        self.tool_id = 0
        self.disk = 0
        self.instanceType = ''

    def to_json(self):
        fields = self.__dict__
        if 'tool_id' in fields:
            fields.pop('tool_id', None)
        return json.dumps(fields, sort_keys=True, indent=4)

class DataStorageRule:
    def __init__(self, file_mask, move_to_sts):
        self.file_mask = file_mask
        self.move_to_sts = move_to_sts

    def match(self, path):
        return fnmatch.fnmatch(path, self.file_mask)

    @staticmethod
    def match_any(rules, path):
        for rule in rules:
            if rule.move_to_sts and rule.match(path):
                return True
        return False

    @staticmethod
    def read_from_file(path):
        if not os.path.exists(path):
            return []
        rules = []
        with open(path, 'r') as rules_file:
            data = rules_file.readline().strip()
            if not data:
                return []
            try:
                for rule in json.loads(data):
                    rules.append(DataStorageRule(rule['fileMask'], rule['moveToSts']))
            except ValueError:
                return rules
        return rules

    @staticmethod
    def write_to_file(path, data):
        with open(path, 'w') as rules_file:
            rules_file.write(str(data))


# enumeration with task statuses, supported by Pipeline API
class TaskStatus:
    SUCCESS, FAILURE, RUNNING, STOPPED, PAUSED = range(5)


# enumeration with task statuses, supported by Pipeline API
class CommmitStatus:
    NOT_COMMITTED, COMMITTING, FAILURE, SUCCESS = range(4)


# Represents a log entry in format supported by Pipeline API
class LogEntry:
    def __init__(self, run_id, status, text, task, instance):
        self.runId = run_id
        self.date = datetime.datetime.utcnow().strftime(DATE_FORMAT)
        self.status = status
        self.logText = text
        self.taskName = task
        self.instance = instance

    def to_json(self):
        return json.dumps(self, default=lambda o: o.__dict__,
                          sort_keys=True, indent=4)


# Represents a status entry in format supported by Pipeline API
class StatusEntry:
    def __init__(self, status):
        self.endDate = datetime.datetime.utcnow().strftime(DATE_FORMAT)
        self.status = status

    def to_json(self):
        return json.dumps(self, default=lambda o: o.__dict__,
                          sort_keys=True, indent=4)


class AclClass:
    PIPELINE = 'PIPELINE'
    FOLDER = 'FOLDER'
    DATA_STORAGE = 'DATA_STORAGE'
    DOCKER_REGISTR = 'DOCKER_REGISTR'
    TOOL = 'TOOL'
    TOOL_GROUP = 'TOOL_GROUP'
    CONFIGURATION = 'CONFIGURATION'
    METADATA_ENTITY = 'METADATA_ENTITY'
    ATTACHMENT = 'ATTACHMENT'


# Represents a PipelineApi Configuration
class PipelineAPI:
    """Represents a PipelineApi Configuration"""

    # Pipeline API endpoint for sending log entries
    LOG_URL = 'run/{}/log'
    # Pipeline API endpoint for sending status updates
    STATUS_URL = 'run/{}/status'
    COMMIT_STATUS_URL = 'run/{}/commitStatus'
    TOOL_URL = 'tool/load?image={image}&registry={registry}'
    TOOL_VERSIONS_URL = 'tool/{tool_id}/tags'
    ENABLE_TOOL_URL = 'tool/register'
    UPDATE_TOOL_URL = 'tool/update'
    DELETE_TOOL_URL = 'tool/delete'
    RUN_URL = 'run'
    GET_RUN_URL = '/run/{}'
    GET_TASK_URL = '/run/{}/task?taskName={}'
    FILTER_RUNS = 'run/filter'
    RUN_COUNT = 'run/count'
    TERMINATE_RUN = 'run/{}/terminate'
    DATA_STORAGE_URL = "/datastorage"
    DATA_STORAGE_LOAD_ALL_URL = "datastorage/loadAll"
    DATA_STORAGE_RULES_URL = "datastorage/rule/load"
    REGISTRY_CERTIFICATES_URL = "dockerRegistry/loadCerts"
    REGISTRY_LOAD_ALL_URL = "dockerRegistry/loadTree"
    TOOL_GROUP_IN_REGISTRY_LOAD_ALL_URL = "/toolGroup/list?registry={}"
    TOOL_GROUP_LOAD_URL = "/toolGroup?id={}"
    SEARCH_RUNS_URL = "/run/search"
    LOAD_PIPELINE_URL = "/pipeline/{}/load"
    LOAD_ALL_PIPELINES_URL = "pipeline/loadAll"
    FIND_PIPELINE_URL = "/pipeline/find?id={}"
    CLONE_PIPELINE_URL = "/pipeline/{}/clone"
    LOAD_WRITABLE_STORAGES = "/datastorage/mount"
    LOAD_AVAILABLE_STORAGES = "/datastorage/available"
    LOAD_LIFECYCLE_RULE_FOR_STORAGE_URL = "/datastorage/{id}/lifecycle/rule/{rule_id}"
    LIFECYCLE_RULES_FOR_STORAGE_URL = "/datastorage/{id}/lifecycle/rule"
    PROLONG_LIFECYCLE_RULES_URL = "/datastorage/{id}/lifecycle/rule/{rule_id}/prolong?path={path}&days={days}&force={force}"
    LIFECYCLE_RULES_EXECUTION_FOR_STORAGE_URL = "/datastorage/{id}/lifecycle/rule/{rule_id}/execution"
    LOAD_LIFECYCLE_RULES_EXECUTION_FOR_STORAGE_URL = "/datastorage/{id}/lifecycle/rule/{rule_id}/execution{filter}"
    UPDATE_STATUS_LIFECYCLE_RULES_EXECUTION_FOR_STORAGE_URL = "/datastorage/{id}/lifecycle/rule/execution/{execution_id}/status?status={status}"
    DELETE_LIFECYCLE_RULES_EXECUTION_URL = "/datastorage/{id}/lifecycle/rule/execution/{execution_id}"
    LOAD_AVAILABLE_STORAGES_WITH_MOUNTS = "/datastorage/availableWithMounts"
    LOAD_STORAGE_ITEM_CONTENT_URL = '/datastorage/{id}/content?path={path}'
    LOAD_METADATA = "/metadata/load"
    SEARCH_METADATA = "/metadata/search?entityClass={entity_class}&key={entity_key}&value={entity_value}"
    SAVE_METADATA_ENTITY = "metadataEntity/save"
    FIND_METADATA_ENTITY = "metadataEntity/loadExternal?id=%s&folderId=%d&className=%s"
    LOAD_ENTITIES_DATA = "/metadataEntity/entities"
    LOAD_DTS = "/dts"
    LOAD_CONFIGURATION = '/configuration/%d'
    GET_PREFERENCE = '/preferences/%s'
    TOOL_VERSION_SETTINGS = '/tool/%d/settings'
    ADD_PIPELINE_REPOSITORY_HOOK = '/pipeline/%s/addHook'
    FOLDER_REGISTER = '/folder/register'
    FOLDER_DELETE = '/folder/%d/delete'
    PIPELINE_CREATE = '/pipeline/register'
    PIPELINE_DELETE = '/pipeline/%d/delete'
    ISSUE_URL = '/issues'
    COMMENT_URL = '/comments'
    NOTIFICATION_URL = '/notification'
    REGION_URL = '/cloud/region'
    LOAD_ALLOWED_INSTANCE_TYPES = '/cluster/instance/allowed?regionId=%s&spot=%s'
    LOAD_PROFILE_CREDENTIALS = 'cloud/credentials/generate/%d'
    LOAD_PROFILES = 'cloud/credentials'
    LOAD_CURRENT_USER = 'whoami'
    LOAD_ROLES = 'role/loadAll?loadUsers={}'
    LOAD_ROLE = 'role/{}'
    LOAD_ROLE_BY_NAME = 'role?name={}'
    LOAD_USER_BY_NAME = 'user?name={}'
    LOAD_USER = 'user/{}'
    RUN_CONFIGURATION = '/runConfiguration'
    NOTIFICATION_SETTING_URL = 'notification/settings'
    NOTIFICATION_TEMPLATE_URL = 'notification/template'
    LIFECYCLE_RESTORE_ACTION_URL = "/datastorage/{id}/lifecycle/restore"
    LIFECYCLE_RESTORE_ACTION_FILTER_URL = "/datastorage/{id}/lifecycle/restore/filter"
    DATA_STORAGE_PATH_SIZE_URL = '/datastorage/path/size'
    SEARCH_DATA_STORAGE_ITEMS_BY_TAG_URL = '/datastorage/tags/search'
    DATA_STORAGE_ITEM_TAG_LIST_URL = '/datastorage/{id}/tags/list?path={path}&showVersions={show_versions}'
    DATA_STORAGE_ITEM_TAGS_BATCH_UPSERT_URL = '/datastorage/{id}/tags/batch/upsert'
    DATA_STORAGE_ITEM_TAGS_BATCH_INSERT_URL = '/datastorage/{id}/tags/batch/insert'
    DATA_STORAGE_ITEM_TAGS_BATCH_DELETE_URL = '/datastorage/{id}/tags/batch/delete'
    DATA_STORAGE_ITEM_TAGS_BATCH_DELETE_ALL_URL = '/datastorage/{id}/tags/batch/deleteAll'
    DATA_STORAGE_LOAD_URL = "/datastorage/{id}/load"
    DATA_STORAGE_LIST_ITEMS_URL = "datastorage/{id}/list"
    DATA_STORAGE_DELETE_URL = '/datastorage/{id}/delete'
    CATEGORICAL_ATTRIBUTE_URL = "/categoricalAttribute"
    GRANT_PERMISSIONS_URL = "/grant"
    PERMISSION_URL = "/permissions"
    RUN_TAG = '/run/{id}/tag'
    REPORT_USERS = "report/users"
    LOG_GROUP = "log/group"
    STORAGE_REQUESTS = "log/storage/requests"
    BILLING_EXPORT = "billing/export"

    # Pipeline API default header

    RESPONSE_STATUS_OK = 'OK'
    MAX_PAGE_SIZE = 400

    def __init__(self, api_url, log_dir, attempts=3, timeout=5, connection_timeout=10):
        urllib3.disable_warnings()
        token = os.environ.get('API_TOKEN')
        self.api_url = api_url
        self.log_dir = log_dir
        self.header = {'content-type': 'application/json',
                       'Authorization': 'Bearer {}'.format(token)}
        self.attempts = attempts
        self.timeout = timeout
        self.connection_timeout = connection_timeout

    def check_response(self, response, not_found_msg=None):
        if response.status_code != 200:
            sys.stderr.write("API responded with status {}\n".format(str(response.status_code)))
            return False
        data = response.json()
        if 'status' in data and data['status'] == self.RESPONSE_STATUS_OK:
            return True
        if 'message' in data:
            sys.stderr.write("API returned error message: {}\n".format(data['message']))
            if not_found_msg and not_found_msg in data['message']:
                return True
            return True
        sys.stderr.write("API responded with not expected message: {}\n".format(str(response)))
        return False

    def execute_request(self, url, method='get', data=None, not_found_msg=None):
        count = 0
        while count < self.attempts:
            count += 1
            try:
                if method == 'get':
                    response = requests.get(url, headers=self.header, verify=False, timeout=self.connection_timeout)
                elif method == 'post':
                    response = requests.post(url, data=data, headers=self.header, verify=False,
                                             timeout=self.connection_timeout)
                elif method == 'delete':
                    response = requests.delete(url, headers=self.header, verify=False, timeout=self.connection_timeout)
                elif method == 'put':
                    response = requests.put(url, data=data, headers=self.header, verify=False,
                                            timeout=self.connection_timeout)
                else:
                    raise RuntimeError('Unsupported request method: {}'.format(method))
                if self.check_response(response, not_found_msg=not_found_msg):
                    result = response.json()
                    return result['payload'] if 'payload' in result else None
            except Exception as e:
                sys.stderr.write('An error has occurred during request to API: {}'.format(str(e.message)))
            time.sleep(self.timeout)
        raise RuntimeError('Exceeded maximum retry count {} for API request'.format(self.attempts))

    def load_run_efficiently(self, run_id):
        return self._request('GET', 'run/' + str(run_id)) or {}

    def log_efficiently(self, run_id, message, task, status, date):
        self._request('POST', 'run/' + str(run_id) + '/log', data={
            'runId': run_id,
            'logText': message,
            'taskName': task,
            'status': status,
            'date': date
        })

    def get_preference_efficiently(self, name):
        return self._request('GET', 'preferences/' + name)

    def get_preference_value(self, name):
        preference = self.get_preference_efficiently(name) or {}
        return preference.get('value')

    def load_current_user_efficiently(self):
        return self._request('GET', 'whoami')

    def _request(self, http_method, endpoint, data=None):
        url = '{}/{}'.format(self.api_url, endpoint)
        count = 0
        exceptions = []
        while count < self.attempts:
            count += 1
            try:
                response = requests.request(method=http_method, url=url, data=json.dumps(data),
                                            headers=self.header, verify=False,
                                            timeout=self.connection_timeout)
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
            time.sleep(self.timeout)
        raise exceptions[-1]

    def _download(self, http_method, endpoint, output_path, data=None):
        # Make sure the output_path's directory exists
        output_dir = os.path.dirname(output_path)
        if output_dir and not os.path.exists(output_dir):
            os.makedirs(output_dir)

        url = '{}/{}'.format(self.api_url, endpoint)
        count = 0
        exceptions = []
        while count < self.attempts:
            count += 1
            try:
                with requests.request(method=http_method, url=url, data=json.dumps(data),
                                      headers=self.header, verify=False,
                                      timeout=self.connection_timeout, stream=True) as r:
                    with open(output_path, 'wb') as f:
                        for chunk in r.iter_content(chunk_size=1024):
                            f.write(chunk)
                        return
            except Exception as e:
                exceptions.append(e)
            time.sleep(self.timeout)
        raise exceptions[-1]

    def load_tool(self, image, registry):
        result = requests.get(str(self.api_url) + self.TOOL_URL.format(image=image, registry=registry),
                              headers=self.header, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError('Failed to load tool {}. API response: {}'.format(image, result.json()['message']))
        payload = result.json()['payload']
        tool = Tool(payload['image'], payload['cpu'], payload['ram'], payload['registry'], payload['registryId'],
                    payload['toolGroupId'])
        if 'labels' in payload:
            tool.labels = payload['labels']
        if 'endpoints' in payload:
            tool.endpoints = payload['endpoints']
        if 'description' in payload:
            tool.description = payload['description']
        if 'shortDescription' in payload:
            tool.shortDescription = payload['shortDescription']
        if 'defaultCommand' in payload:
            tool.defaultCommand = payload['defaultCommand']
        if 'instanceType' in payload:
            tool.instanceType = payload['instanceType']
        if 'disk' in payload:
            tool.disk = payload['disk']
        if 'id' in payload:
            tool.tool_id = payload['id']
        return tool

    def load_tool_versions(self, tool_id):
        result = requests.get(str(self.api_url) + self.TOOL_VERSIONS_URL.format(tool_id=tool_id),
                              headers=self.header, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError('Failed to load tool versions {}. API response: {}'.format(tool_id, result.json()['message']))
        return result.json()['payload']

    def enable_tool(self, tool):
        result = requests.post(str(self.api_url) + self.ENABLE_TOOL_URL, data=tool.to_json(),
                               headers=self.header, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError('Failed to enable tool {}/{}. API response: {}'.format(tool.registry, tool.image, result.json()['message']))

    def update_tool(self, tool):
        result = requests.post(str(self.api_url) + self.UPDATE_TOOL_URL, data=tool.to_json(),
                               headers=self.header, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError('Failed to update tool {}/{}. API response: {}'.format(tool.registry, tool.image, result.json()['message']))

    def load_datastorage_rules(self, pipeline_id):
        params = {"pipelineId": pipeline_id}
        result = requests.get(str(self.api_url) + self.DATA_STORAGE_RULES_URL,
                              headers=self.header, params=params, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            return None
        result_json = result.json()
        if not 'payload' in result_json:
            return None
        payload = json.dumps(result_json['payload'])

        if payload:
            return payload

        return None

    def load_certificates(self):
        result = requests.get(str(self.api_url) + self.REGISTRY_CERTIFICATES_URL, headers=self.header, verify=False)
        result_json = result.json()
        if hasattr(result_json, 'error') or result_json['status'] != self.RESPONSE_STATUS_OK:
            return None
        if not 'payload' in result_json:
            return None
        payload = json.dumps(result_json['payload'])
        return json.loads(payload)

    def load_run(self, run_id):
        try:
            result = self.execute_request(str(self.api_url) + self.GET_RUN_URL.format(run_id))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to load run.", "Error message: {}".format(str(e.message)))

    def load_task(self, run_id, task_name, parameters=None):
        url = self.GET_TASK_URL.format(run_id, task_name)
        if parameters:
            url += "&parameters={}".format(parameters)
        try:
            return self.execute_request(str(self.api_url) + url)
        except Exception as e:
            raise RuntimeError("Failed to load task {}. API response: {}".format(run_id, str(e.message)))
    
    def launch_pipeline(self, pipeline_id, pipeline_version, parameters,
                        cmd=None, docker=None, instance=None, disk=None, parent_node_id=None, parent_run_id=None):
        request = {'pipelineId': pipeline_id, 'version': pipeline_version, 'params': parameters}

        if parent_node_id:
            request['parentNodeId'] = parent_node_id
        if parent_run_id:
            request['parentRunId'] = parent_run_id
        if cmd:
            request['cmdTemplate'] = cmd
        if docker:
            request['dockerImage'] = docker
        if instance:
            request['instanceType'] = instance
        if disk:
            request['hddSize'] = disk

        result = requests.post(str(self.api_url) + self.RUN_URL,
                               data=json.dumps(request), headers=self.header, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError(result.json()['message'])
        return result.json()['payload']

    def launch_pod(self, parent_run, cmd, docker_image):
        request = {'cmdTemplate': cmd, 'dockerImage': docker_image, 'useRunId': parent_run}
        result = requests.post(str(self.api_url) + self.RUN_URL,
                               data=json.dumps(request), headers=self.header, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError(result.json()['message'])
        return result.json()['payload']['podId']

    def load_child_pipelines(self, parent_id):
        request = {'page': '1', 'pageSize': self.MAX_PAGE_SIZE, 'partialParameters': 'parent_id={}'.format(parent_id)}
        result = requests.post(str(self.api_url) + self.FILTER_RUNS,
                               data=json.dumps(request), headers=self.header, verify=False)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError(result.json()['message'])
        return result.json()['payload']['elements']

    def search_runs(self, parameters, status=None, run_id=None):
        if not parameters:
            raise RuntimeError("Parameters are required to search pipeline runs")
        request = {'page': '1', 'pageSize': self.MAX_PAGE_SIZE, 'timezoneOffsetInMinutes': 0}
        expression_params = []
        for param in parameters:
            expression_params.append(("parameter.{}".format(param[0]), param[1]))
        if status is not None:
            expression_params.append(("status", status))
        if run_id is not None:
            expression_params.append(("id", str(run_id)))
        if len(expression_params) == 1:
            parameter = expression_params[0]
            request["filterExpression"] = self.parameter_json(parameter[0], parameter[1])
        else:
            expressions = []
            parameter = expression_params.pop()
            expressions.append(self.parameter_json(parameter[0], parameter[1]))
            parameter = expression_params.pop()
            expressions.append(self.parameter_json(parameter[0], parameter[1]))
            current_expression = {"filterExpressionType": "AND", "expressions": expressions}
            while len(expression_params) > 0:
                parameter = expression_params.pop()
                current_expression = {"filterExpressionType": "AND",
                                       "expressions": [current_expression, self.parameter_json(parameter[0], parameter[1])]}
            request["filterExpression"] = current_expression
        result = requests.post(str(self.api_url) + self.SEARCH_RUNS_URL,
                               data=json.dumps(request), headers=self.header, verify=False)

        result_json = result.json()
        if hasattr(result_json, 'error'):
            raise RuntimeError(result_json['error'])
        if result_json['status'] != self.RESPONSE_STATUS_OK:
            raise RuntimeError(result_json['message'])
        if 'payload' not in result_json or 'elements' not in result_json['payload']:
            return []
        return result_json['payload']['elements']

    def parameter_json(self, field, value):
        return {"field": field, "value": value,
                "filterExpressionType": "LOGICAL", "operand": "="}

    def log_event(self, log_entry, log_file_name=None, omit_console=False):
        log_entry.date = datetime.datetime.utcfromtimestamp(time.time()).strftime(DATE_FORMAT)
        log_entry.date = log_entry.date[0:len(log_entry.date) - 3]
        if log_file_name is None:
            log_file_name = "{}.log".format(log_entry.taskName)
        try:
            log_text_formatted = "[{}]\t{}\t{}\n".format(log_entry.date, log_entry.status, log_entry.taskName)
            if not omit_console:
                print(log_entry.logText)

            try:
                if self.api_url:
                    requests.post(str(self.api_url) + self.LOG_URL.format(log_entry.runId),
                                  data=log_entry.to_json(), headers=self.header, verify=False)
            except Exception as api_e:
                if not omit_console:
                    print("Failed to save logs to API, logs will be stored to text file")

            try:
                if not os.path.exists(self.log_dir):
                    os.makedirs(self.log_dir)

                log_path = os.path.join(self.log_dir, log_file_name)
                with open(log_path, "a") as log_file:
                    log_file.write(log_text_formatted)
                    log_file.write(log_entry.logText)
                    if not log_entry.logText.endswith("\n"):
                        log_file.write("\n")

            except Exception as file_e:
                if not omit_console:
                    print("Failed to save logs to file")
        except Exception as e:
            if not omit_console:
                print("Failed to save task log: " + str(e.message))

    def docker_registry_load_all(self):
        try:
            result = requests.get(str(self.api_url) + self.REGISTRY_LOAD_ALL_URL, headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed to load docker registries. API response: {}'.format(result.json()['message']))
            return result.json()['payload']['registries']
        except Exception as e:
            raise RuntimeError("Failed to load docker registries. \n {}".format(e))

    def tool_group_in_registry_load_all(self, registry):
        try:
            result = requests.get(str(self.api_url) + self.TOOL_GROUP_IN_REGISTRY_LOAD_ALL_URL.format(registry), headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed to load tool groups. API response: {}'.format(result.json()['message']))
            return result.json()['payload']
        except Exception as e:
            raise RuntimeError("Failed to load tool groups. \n {}".format(e))

    def tool_group_load(self, id):
        try:
            result = requests.get(str(self.api_url) + self.TOOL_GROUP_LOAD_URL.format(id), headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed to load tool group. API response: {}'.format(result.json()['message']))
            return result.json()['payload']
        except Exception as e:
            raise RuntimeError("Failed to load tool group. \n {}".format(e))

    def update_status(self, run_id, status_entry):
        status_entry.endDate = datetime.datetime.utcfromtimestamp(time.time()).strftime(DATE_FORMAT)
        status_entry.endDate = status_entry.endDate[0:len(status_entry.endDate) - 3]
        try:
            requests.post(str(self.api_url) + self.STATUS_URL.format(run_id), data=status_entry.to_json(),
                          headers=self.header, verify=False)
        except:
            print("Failed to update task status.")

    def update_commit_status(self, run_id, status):
        try:
            commit_status_json = json.dumps({"commitStatus": status})
            result = requests.post(str(self.api_url) + self.COMMIT_STATUS_URL.format(run_id),
                                   data=commit_status_json, headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed update commit run status. API response: {}'.format(result.json()['message']))
            return result.json()['payload']
        except Exception as e:
            raise RuntimeError("Failed to update commit status. \n {}".format(e))

    def load_pipeline(self, pipeline_id):
        try:
            result = requests.get(str(self.api_url) + self.LOAD_PIPELINE_URL.format(pipeline_id),
                                  headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed fetch pipeline info. API response: {}'.format(result.json()['message']))
            return result.json()['payload']
        except Exception as e:
            raise RuntimeError("Failed to fetch pipeline info. \n {}".format(e))

    def load_all_pipelines(self):
        try:
            result = requests.get(str(self.api_url) + self.LOAD_ALL_PIPELINES_URL, headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed to fetch all pipelines info. API response: {}'.format(result.json()['message']))
            return result.json()['payload']
        except Exception as e:
            raise RuntimeError("Failed to fetch all pipelines info. \n {}".format(e))

    def find_pipeline(self, pipeline_name):
        try:
            result = requests.get(str(self.api_url) + self.FIND_PIPELINE_URL.format(pipeline_name),
                                  headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed fetch pipeline info. API response: {}'.format(result.json()['message']))
            return result.json()['payload']
        except Exception as e:
            raise RuntimeError("Failed to fetch pipeline info. \n {}".format(e))

    def get_pipeline_clone_url(self, pipeline_id):
        try:
            result = requests.get(str(self.api_url) + self.CLONE_PIPELINE_URL.format(pipeline_id),
                                  headers=self.header, verify=False)
            if hasattr(result.json(), 'error') or result.json()['status'] != self.RESPONSE_STATUS_OK:
                raise RuntimeError('Failed fetch pipeline info. API response: {}'.format(result.json()['message']))
            return result.json()['payload']
        except Exception as e:
            raise RuntimeError("Failed to update commit status. \n {}".format(e))

    def load_writable_storages(self):
        try:
            result = self.execute_request(str(self.api_url) + self.LOAD_WRITABLE_STORAGES)
            if result is None:
                return []
            return [DataStorage.from_json(item) for item in result]
        except Exception as e:
            raise RuntimeError("Failed to load storages with READ and WRITE permissions. "
                               "Error message: {}".format(str(e.message)))

    def load_available_storages(self):
        try:
            result = self.execute_request(str(self.api_url) + self.LOAD_AVAILABLE_STORAGES)
            if result is None:
                return []
            return [DataStorage.from_json(item) for item in result]
        except Exception as e:
            raise RuntimeError("Failed to load storages with READ and WRITE permissions. "
                               "Error message: {}".format(str(e.message)))

    def load_available_storages_with_share_mount(self, from_region_id=None):
        try:
            url = str(self.api_url) + self.LOAD_AVAILABLE_STORAGES_WITH_MOUNTS
            if from_region_id is not None:
                url += "?fromRegion={}".format(from_region_id)
            result = self.execute_request(url)
            if result is None:
                return []
            return [DataStorageWithShareMount.from_json(item) for item in result]
        except Exception as e:
            raise RuntimeError("Failed to load storages with READ and WRITE permissions. "
                               "Error message: {}".format(str(e.message)))

    def load_metadata(self, entity_id, entity_class):
        try:
            data = [{
                "entityId": entity_id,
                "entityClass": entity_class
            }]
            result = self.execute_request(str(self.api_url) + self.LOAD_METADATA,
                                          method="post",
                                          data=json.dumps(data))
            if not result or not "data" in result[0]:
                return []
            return result[0]["data"]
        except Exception as e:
            raise RuntimeError("Failed to load metadata for the given entity. "
                               "Error message: {}".format(str(e.message)))

    def search_metadata(self, entity_key, entity_value, entity_class):
        try:
            suffix = self.SEARCH_METADATA.format(entity_key=entity_key,
                                              entity_value=entity_value,
                                              entity_class=entity_class)
            result = self.execute_request(str(self.api_url) + suffix,
                                          method="get")
            if not result or len(result) == 0:
                return []
            return result
        except Exception as e:
            raise RuntimeError("Failed to search metadata for the given entity. "
                               "Error message: {}".format(str(e.message)))

    def load_metadata_efficiently(self, entity_id, entity_class):
        all_metadata = self.load_all_metadata_efficiently([entity_id], entity_class)
        return (all_metadata[0] if all_metadata else {}).get('data', {})

    def load_all_metadata_efficiently(self, entity_ids, entity_class):
        data = [{"entityId": entity_id, "entityClass": entity_class} for entity_id in entity_ids]
        return self._request('POST', self.LOAD_METADATA, data=data) or []

    def load_entities(self, entities_ids):
        try:
            result = self.execute_request(str(self.api_url) + self.LOAD_ENTITIES_DATA, method='post',
                                          data="[%s]" % entities_ids)
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to load entities data. "
                               "Error message: {}".format(str(e.message)))

    def save_metadata_entity(self, entity):
        try:
            result = self.execute_request(str(self.api_url) + self.SAVE_METADATA_ENTITY, method='post',
                                          data=json.dumps(entity))
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to save metadata entities. "
                               "Error message: {}".format(str(e.message)))

    def find_metadata_entity(self, folder_id, external_id, class_name):
        try:
            result = self.execute_request(str(self.api_url) +
                                          self.FIND_METADATA_ENTITY % (external_id, folder_id, class_name),
                                          method='get', not_found_msg='not found')
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to find metadata entities. "
                               "Error message: {}".format(str(e.message)))

    def download_metadata_entities(self, output_path, folder_id, entity_class, entity_ids=None, file_format=None):
        endpoint = 'metadataEntity/download?folderId={}&entityClass={}'.format(folder_id, entity_class)
        if entity_ids:
            endpoint += '&entityIds={}'.format(','.join(map(str, entity_ids)))
        if file_format:
            endpoint += '&fileFormat={}'.format(file_format)
        self._download('GET', endpoint, output_path=output_path)

    def load_dts_registry(self):
        try:
            result = self.execute_request(str(self.api_url) + self.LOAD_DTS, method='get')
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to load DTS registry. "
                               "Error message: {}".format(str(e.message)))

    def load_configuration(self, configuration_id):
        try:
            result = self.execute_request(str(self.api_url) + self.LOAD_CONFIGURATION % configuration_id, method='get')
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to load configuration. "
                               "Error message: {}".format(str(e.message)))

    def get_preference(self, preference_name):
        try:
            result = self.execute_request(str(self.api_url) + self.GET_PREFERENCE % preference_name, method='get')
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to get system preference %s. "
                               "Error message: %s" % (preference_name, e.message))

    def get_contextual_preference(self, preference_name, preference_level, resource_id):
        try:
            url = self.api_url \
                  + '/contextual/preference/load?name=' +  preference_name \
                  + '&level=' + preference_level \
                  + '&resourceId=' + str(resource_id)
            result = self.execute_request(url, method='get')
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to get contextual preference %s for %s level and resource id %s. "
                               "Error message: %s" % (preference_name, preference_level, str(resource_id), e.message))

    # "preference_level" accepts only "TOOL" value for now. Any other value will throw an error
    # "resource_id"=-1 is used when you don't need to consider the tool's setting. Only user and group
    def search_contextual_preference(self, preference_name, preference_level="TOOL", resource_id=-1):
        try:
            url = self.api_url + '/contextual/preference'
            data = {
                "preferences": [ 
                    preference_name
                ],
                "resource": {
                    "level": "TOOL",
                    "resourceId": resource_id
                }
            }
            result = self.execute_request(self.api_url + '/contextual/preference', 
                                            method='post',
                                            data=json.dumps(data))
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to search contextual preference %s for %s level and resource id %s. "
                               "Error message: %s" % (preference_name, preference_level, str(resource_id), e.message))

    def load_tool_version_settings(self, tool_id, version):
        get_tool_version_settings_url = self.TOOL_VERSION_SETTINGS % tool_id
        if version:
            get_tool_version_settings_url = "%s?version=%s" % (get_tool_version_settings_url, version)
        try:
            result = self.execute_request(str(self.api_url) + get_tool_version_settings_url, method='get')
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to load settings for tool %d. "
                               "Error message: %s" % (tool_id, e.message))

    def create_setting_for_tool_version(self, tool_id, version, settings):
        tool_version_settings_url = self.TOOL_VERSION_SETTINGS % tool_id
        tool_version_settings_url = "%s?version=%s" % (tool_version_settings_url, version)
        try:
            result = self.execute_request(str(self.api_url) + tool_version_settings_url, method='post',
                                          data=json.dumps(settings))
            return {} if result is None else result
        except BaseException as e:
            raise RuntimeError("Failed to load settings for tool %d. "
                               "Error message: %s" % (tool_id, e.message))

    def add_pipeline_repository_hook(self, pipeline_id):
        try:
            result = self.execute_request(str(self.api_url) + self.ADD_PIPELINE_REPOSITORY_HOOK % str(pipeline_id),
                                          method='post')
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to add hook to pipeline repository. \n {}".format(e))

    def search(self, query, types, page_size=50, offset=0, aggregate=True, highlight=True):
        try:
            data = {
                "query": query,
                "pageSize": page_size,
                "offset": offset,
                "aggregate": aggregate,
                "highlight": highlight,
                "filterTypes": types
            }
            result = self.execute_request(str(self.api_url) + '/search', method='post', data=json.dumps(data))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError('Search failed for query %s and %s types' % (query, ','.join(types)),
                               "Error message: %s" % e.message)

    def create_folder(self, name, parent_id=None):
        try:
            data = {
                "name": name,
                "parentId": parent_id
            }
            result = self.execute_request(str(self.api_url) + self.FOLDER_REGISTER,
                                          method="post",
                                          data=json.dumps(data))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to create folder with name %s." % name,
                               "Error message: {}".format(str(e.message)))

    def delete_folder(self, folder_id):
        try:
            self.execute_request(str(self.api_url) + self.FOLDER_DELETE % folder_id,
                                 method="delete")
        except Exception as e:
            raise RuntimeError("Failed to delete folder with ID %d." % folder_id,
                               "Error message: {}".format(str(e.message)))

    def create_pipeline(self, pipeline_data):
        try:
            result = self.execute_request(str(self.api_url) + self.PIPELINE_CREATE, method="post",
                                          data=json.dumps(pipeline_data))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to create pipeline.", "Error message: {}".format(str(e.message)))

    def delete_pipeline(self, pipeline_id):
        try:
            self.execute_request(str(self.api_url) + self.PIPELINE_DELETE % pipeline_id,
                                 method="delete")
        except Exception as e:
            raise RuntimeError("Failed to delete pipeline with ID %d." % pipeline_id,
                               "Error message: {}".format(str(e.message)))

    def datastorage_create(self, datastorage_data, process_on_cloud=True):
        try:
            url = str(self.api_url) + '/datastorage/save' + '?cloud=%s' % str(process_on_cloud).lower()
            result = self.execute_request(url, method="post", data=json.dumps(datastorage_data))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to create pipeline.", "Error message: {}".format(str(e.message)))

    def delete_datastorage(self, datastorage_id, process_on_cloud=True):
        try:
            url = '/datastorage/%d/delete?cloud=%s' % (datastorage_id, str(process_on_cloud).lower())
            self.execute_request(str(self.api_url) + url, method="delete")
        except Exception as e:
            raise RuntimeError("Failed to delete data storage with ID %d." % datastorage_id,
                               "Error message: {}".format(str(e.message)))

    def create_issue(self, name, text, entity_id, entity_class):
        try:
            data = {
                "name": name,
                "text": text,
                "entity": {
                    "entityId": entity_id,
                    "entityClass": entity_class
                }
            }
            result = self.execute_request(str(self.api_url) + self.ISSUE_URL, method="post", data=json.dumps(data))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to create issue.", "Error message: {}".format(str(e.message)))

    def delete_issue(self, issue_id):
        try:
            self.execute_request(str(self.api_url) + self.ISSUE_URL + "/" + issue_id, method="delete")
        except Exception as e:
            raise RuntimeError("Failed to delete issue.", "Error message: {}".format(str(e.message)))

    def create_comment(self, issue_id, text):
        try:
            data = {
                "text": text,
            }
            result = self.execute_request(str(self.api_url) + self.ISSUE_URL + "/" + str(issue_id) + self.COMMENT_URL,
                                          method="post", data=json.dumps(data))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to create issue comment.", "Error message: {}".format(str(e.message)))

    def create_notification(self, subject, body, to_user, copy_users=None, parameters=None):
        try:
            data = {
                "subject": subject,
                "body": body,
                "toUser": to_user,
                "copyUsers": copy_users,
                "parameters": parameters
            }
            result = self.execute_request(str(self.api_url) + self.NOTIFICATION_URL + "/message", method="post",
                                          data=json.dumps(data))
            return {} if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to create a notification.", "Error message: {}".format(str(e.message)))

    def generate_temporary_credentials(self, actions):
        try:
            if actions:
                data = []
                for id, read, write in actions:
                    data.append({
                        "id": id,
                        "read": read,
                        "write": write
                    })
                result = self.execute_request(str(self.api_url) + self.DATA_STORAGE_URL + "/tempCredentials/",
                                              method="post", data=json.dumps(data))
                return {} if result is None else result
            else:
                raise RuntimeError("No actions are specified to generate credentials for.")
        except Exception as e:
            raise RuntimeError("Failed to generate temporary credentials.", "Error message: {}".format(str(e.message)))

    def get_regions(self):
        try:
            result = self.execute_request(str(self.api_url) + self.REGION_URL, method="get")
            return [] if result is None else [CloudRegion.from_json(region_json) for region_json in result]
        except Exception as e:
            raise RuntimeError("Failed to get all regions.", "Error message: {}".format(str(e.message)))

    def find_datastorage(self, id):
        """
        Searches for a data storage by its id or name.

        :param id: Data storage id or name.
        :return: Data storage.
        """
        try:
            result = self.execute_request(str(self.api_url) + self.DATA_STORAGE_URL + '/find?id=' + id)
            return {} if result is None else DataStorage.from_json(result)
        except Exception as e:
            raise RuntimeError("Failed to find storage by its id or name. "
                               "Error message: {}".format(str(e.message)))

    def get_allowed_instance_types(self, region_id, spot=False):
        try:
            url = str(self.api_url) + self.LOAD_ALLOWED_INSTANCE_TYPES % (str(region_id), str(spot).lower())
            return self.execute_request(url, method='get')
        except Exception as e:
            raise RuntimeError("Failed to get allowed instances for region %s." % region_id,
                               "Error message: %s" % str(e.message))

    def get_storage_download_url(self, storage_id, paths):
        try:
            data = {
                'paths': paths
            }
            url = str(self.api_url) + self.DATA_STORAGE_URL + '/%s/generateUrl' % str(storage_id)
            result = self.execute_request(url, method='post', data=json.dumps(data))
            return [] if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to load generated download url for storage ID {}. "
                               "Error message: {}".format(str(storage_id), str(e.message)))

    def get_storage_upload_url(self, storage_id, paths):
        try:
            url = str(self.api_url) + self.DATA_STORAGE_URL + '/%s/generateUploadUrl' % str(storage_id)
            result = self.execute_request(url, method='post', data=json.dumps(paths))
            return [] if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to load generated upload url for storage ID {}. "
                               "Error message: {}".format(str(storage_id), str(e.message)))

    def load_profile_credentials(self, profile_id, region_id=None):
        try:
            url = str(self.api_url) + self.LOAD_PROFILE_CREDENTIALS % int(profile_id)
            if region_id:
                url += '?regionId=%d' % int(region_id)
            return self.execute_request(url, method='get')
        except Exception as e:
            raise RuntimeError("Failed to generate profile credentials for profile ID '{}'. "
                               "Error message: {}".format(str(profile_id), str(e.message)))

    def load_profiles_for_user(self, user_id=None):
        try:
            url = str(self.api_url) + self.LOAD_PROFILES
            if user_id:
                url += '?userId=%d' % int(user_id)
            result = self.execute_request(url, method='get')
            return [] if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to load profile credentials. Error message: {}".format(str(e.message)))

    def get_region(self, region_id):
        try:
            url = str(self.api_url) + self.REGION_URL + '/%d' % region_id
            return self.execute_request(url, method="get")
        except Exception as e:
            raise RuntimeError("Failed to get region by ID '{}'. Error message: {}".format(str(region_id),
                                                                                           str(e.message)))

    def load_current_user(self):
        try:
            url = str(self.api_url) + self.LOAD_CURRENT_USER
            return self.execute_request(url, method='get')
        except Exception as e:
            raise RuntimeError("Failed to load current user. Error message: {}".format(str(e.message)))

    def generate_user_token(self, user_name, duration=None):
        try:
            if duration:
                expiration_query = '&expiration=' + str(duration)
            url = str(self.api_url) \
                  + '/user/token?name=' + user_name \
                  + (expiration_query if duration else '')
            return self.execute_request(url, method='get')
        except Exception as e:
            raise RuntimeError("Failed to load user token. Error message: {}".format(str(e.message)))

    def load_roles(self, load_users=False):
        try:
            return self.execute_request(str(self.api_url) + self.LOAD_ROLES.format(load_users)) or []
        except Exception as e:
            raise RuntimeError("Failed to load roles.", "Error message: {}".format(str(e.message)))

    def load_role(self, role_id):
        try:
            return self.execute_request(str(self.api_url) + self.LOAD_ROLE.format(role_id))
        except Exception as e:
            raise RuntimeError("Failed to load role by ID '{}'.", "Error message: {}".format(str(role_id),
                                                                                             str(e.message)))

    def load_user_by_name(self, user):
        try:
            return self.execute_request(str(self.api_url) + self.LOAD_USER_BY_NAME.format(user))
        except Exception as e:
            raise RuntimeError("Failed to load user by name '{}'.", "Error message: {}".format(str(user),
                                                                                               str(e.message)))

    def load_user(self, user_id):
        try:
            return self.execute_request(str(self.api_url) + self.LOAD_USER.format(user_id))
        except Exception as e:
            raise RuntimeError("Failed to load user by id '{}'.", "Error message: {}".format(str(user_id),
                                                                                             str(e.message)))

    def load_role_by_name(self, name):
        try:
            return self.execute_request(str(self.api_url) + self.LOAD_ROLE_BY_NAME.format(name))
        except Exception as e:
            raise RuntimeError("Failed to load role by name '{}'.", "Error message: {}".format(str(name),
                                                                                               str(e.message)))

    def load_users(self):
        return self._request('GET', 'users') or []

    def run_configuration(self, data):
        try:
            result = self.execute_request(str(self.api_url) + self.RUN_CONFIGURATION, method='post',
                                          data=json.dumps(data))
            return [] if result is None else result
        except Exception as e:
            raise RuntimeError("Failed to launch configuration {}. "
                               "Error message: {}".format(str(data['id']), str(e.message)))

    def get_edge_external_url(self, region=None):
        endpoint = 'cluster/edge/externalUrl'
        if region:
            endpoint += '?region=' + region
        return self._request('GET', endpoint)

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        try:
            return self.execute_request(str(self.api_url) +
                                        self.LIFECYCLE_RULES_FOR_STORAGE_URL.format(id=datastorage_id))
        except Exception as e:
            raise RuntimeError("Failed to load lifecycle rules by datastorage ID '{}'.",
                               "Error message: {}".format(str(datastorage_id), str(e.message)))

    def load_lifecycle_rule(self, datastorage_id, rule_id):
        try:
            return self.execute_request(str(self.api_url) +
                                        self.LOAD_LIFECYCLE_RULE_FOR_STORAGE_URL.format(
                                            id=datastorage_id, rule_id=rule_id))
        except Exception as e:
            raise RuntimeError("Failed to load lifecycle rule by ID '{}'.",
                               "Error message: {}".format(str(rule_id), str(e.message)))

    def create_lifecycle_rule_execution(self, datastorage_id, rule_id, execution):
        try:
            return self.execute_request(str(self.api_url) + self.LIFECYCLE_RULES_EXECUTION_FOR_STORAGE_URL.format(
                                            id=datastorage_id, rule_id=rule_id), data=json.dumps(execution),
                                        method='post')
        except Exception as e:
            raise RuntimeError("Failed to create lifecycle rule execution for rule ID '{}'.",
                               "Error message: {}".format(str(rule_id), str(e.message)))

    def create_lifecycle_rule(self, datastorage_id, rule):
        try:
            return self.execute_request(str(self.api_url) + self.LIFECYCLE_RULES_FOR_STORAGE_URL.format(
                                            id=datastorage_id), data=json.dumps(rule), method='post')
        except Exception as e:
            raise RuntimeError("Failed to create lifecycle rule for datastorage ID '{}'.",
                               "Error message: {}".format(str(datastorage_id), str(e.message)))

    def delete_lifecycle_rule(self, datastorage_id, rule_id):
        try:
            return self.execute_request(str(self.api_url) + self.LOAD_LIFECYCLE_RULE_FOR_STORAGE_URL.format(
                                            id=datastorage_id, rule_id=rule_id), method='delete')
        except Exception as e:
            raise RuntimeError("Failed to create lifecycle rule for datastorage ID '{}'.",
                               "Error message: {}".format(str(datastorage_id), str(e.message)))

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id, path=None, status=None):

        def _format_param(param_str, name, value):
            if param_str:
                return param_str + "&{}={}".format(name, value)
            else:
                return "?{}={}".format(name, value)

        try:
            params = None
            if path:
                params = _format_param(params, "path", path)
            if status:
                params = _format_param(params, "status", status)

            return self.execute_request(str(self.api_url) +
                                        self.LOAD_LIFECYCLE_RULES_EXECUTION_FOR_STORAGE_URL.format(
                                            id=datastorage_id, rule_id=rule_id, filter=params if params else ""))
        except Exception as e:
            raise RuntimeError("Failed to load lifecycle rule executions for rule ID '{}'.",
                               "Error message: {}".format(str(rule_id), str(e.message)))

    def update_status_lifecycle_rule_execution(self, datastorage_id, execution_id, status):
        try:
            return self.execute_request(str(self.api_url) +
                                        self.UPDATE_STATUS_LIFECYCLE_RULES_EXECUTION_FOR_STORAGE_URL.format(
                                            id=datastorage_id, execution_id=execution_id, status=status), method='put')
        except Exception as e:
            raise RuntimeError("Failed to update lifecycle rule execution status by ID '{}' status to update: {}.",
                               "Error message: {}".format(str(execution_id), status, str(e.message)))

    def delete_lifecycle_rule_execution(self, datastorage_id, execution_id):
        try:
            return self.execute_request(str(self.api_url) +
                                        self.DELETE_LIFECYCLE_RULES_EXECUTION_URL.format(
                                            id=datastorage_id, execution_id=execution_id), method='delete')
        except Exception as e:
            raise RuntimeError("Failed to delete lifecycle rule execution by ID '{}'.",
                               "Error message: {}".format(str(execution_id), str(e.message)))

    def prolong_lifecycle_rule(self, datastorage_id, rule_id, path, days, force=False):
        try:
            return self.execute_request(str(self.api_url) + self.PROLONG_LIFECYCLE_RULES_URL.format(
                                            id=datastorage_id, rule_id=rule_id, path=path, days=days, force=force))
        except Exception as e:
            raise RuntimeError("Failed to prolong lifecycle rule '{}'.",
                               "Error message: {}".format(str(rule_id), str(e.message)))

    def load_datastorage_item_content(self, storage_id, path, version=None):
        try:
            formed_url = self.LOAD_STORAGE_ITEM_CONTENT_URL.format(id=storage_id, path=path)
            if version:
                formed_url = formed_url + "&version={version}".format(version=version)
            return self._request(endpoint=formed_url, http_method="get")
        except Exception as e:
            raise RuntimeError("Failed to load datastorage item content: storage_id - '{}', path - '{}', version - '{}'.",
                               "Error message: {}".format(storage_id, path, version, str(e.message)))

    def search_datastorage_items_by_tag(self, request):
        try:
            return self._request(
                endpoint=self.SEARCH_DATA_STORAGE_ITEMS_BY_TAG_URL, http_method="post", data=request
            )
        except Exception as e:
            raise RuntimeError("Failed to search datastorage items by tag: request - '{}'.",
                               "Error message: {}".format(request, str(e.message)))

    def load_datastorage(self, storage_id):
        try:
            return self._request(endpoint=self.DATA_STORAGE_LOAD_URL.format(id=storage_id), http_method="get")
        except Exception as e:
            raise RuntimeError("Failed to load datastorage by id: '{}'.",
                               "Error message: {}".format(storage_id, str(e.message)))

    def load_datastorage_item_with_tags(self, storage_id, path, show_versions=False):
        try:
            return self._request(
                endpoint=self.DATA_STORAGE_ITEM_TAG_LIST_URL.format(id=storage_id, path=path, show_versions=show_versions),
                http_method="get"
            )
        except Exception as e:
            raise RuntimeError("Failed to load datastorage item with tags: storage_id - '{}', path - '{}', show_versions - '{}'.",
                               "Error message: {}".format(storage_id, path, show_versions, str(e.message)))

    def insert_datastorage_item_tags(self, storage_id, request):
        try:
            return self._request(
                endpoint=self.DATA_STORAGE_ITEM_TAGS_BATCH_INSERT_URL.format(id=storage_id), http_method="put", data=request
            )
        except Exception as e:
            raise RuntimeError("Failed to batch insert tags: request - '{}'.",
                               "Error message: {}".format(request, str(e.message)))

    def upsert_datastorage_item_tags(self, storage_id, request):
        try:
            return self._request(
                endpoint=self.DATA_STORAGE_ITEM_TAGS_BATCH_UPSERT_URL.format(id=storage_id), http_method="put", data=request
            )
        except Exception as e:
            raise RuntimeError("Failed to batch upsert tags: request - '{}'.",
                               "Error message: {}".format(request, str(e.message)))

    def delete_datastorage_item_tags(self, storage_id, request):
        try:
            return self._request(
                endpoint=self.DATA_STORAGE_ITEM_TAGS_BATCH_DELETE_URL.format(id=storage_id), http_method="delete", data=request
            )
        except Exception as e:
            raise RuntimeError("Failed to batch delete tags: request - '{}'.",
                               "Error message: {}".format(request, str(e.message)))

    def delete_all_datastorage_item_tags(self, storage_id, request):
        try:
            return self._request(
                endpoint=self.DATA_STORAGE_ITEM_TAGS_BATCH_DELETE_ALL_URL.format(id=storage_id), http_method="delete", data=request
            )
        except Exception as e:
            raise RuntimeError("Failed to batch delete all tags: request - '{}'.",
                               "Error message: {}".format(request, str(e.message)))

    def load_notification_templates(self):
        try:
            return self.execute_request(str(self.api_url) + self.NOTIFICATION_TEMPLATE_URL)
        except Exception as e:
            raise RuntimeError("Failed to load notification templates. Error message {}", str(e.message))

    def load_notification_settings(self):
        try:
            return self.execute_request(str(self.api_url) + self.NOTIFICATION_SETTING_URL)
        except Exception as e:
            raise RuntimeError("Failed to load notification settings. Error message {}", str(e.message))

    def filter_lifecycle_restore_action(self, datastorage_id, filter_obj):
        try:
            return self.execute_request(str(self.api_url) +
                                        self.LIFECYCLE_RESTORE_ACTION_FILTER_URL.format(id=datastorage_id),
                                        data=json.dumps(filter_obj),  method='post')
        except Exception as e:
            raise RuntimeError(
                "Failed to load lifecycle restore actions for storage: '{}', and filters: '{}'.".format(
                    str(datastorage_id), filter_obj), "Error message: {}".format(str(e.message)))

    def update_lifecycle_restore_action(self, datastorage_id, restore_action):
        try:
            return self.execute_request(str(self.api_url) +
                                        self.LIFECYCLE_RESTORE_ACTION_URL.format(id=datastorage_id),
                                        data=json.dumps(restore_action), method='put')
        except Exception as e:
            raise RuntimeError(
                "Failed to update lifecycle restore actions for storage: '{}', action: '{}'.".format(
                    str(datastorage_id), restore_action), "Error message: {}".format(str(e.message)))

    def get_paths_size(self, paths):
        try:
            return self.execute_request(str(self.api_url) +
                                        self.DATA_STORAGE_PATH_SIZE_URL,
                                        data=json.dumps(paths), method='post')
        except Exception as e:
            raise RuntimeError(
                "Failed get size for paths: '{}'. Error message: {}".format(','.join(paths), str(e.message)))

    def load_categorical_attributes_dictionary(self):
        try:
            return self._request(endpoint=self.CATEGORICAL_ATTRIBUTE_URL, http_method="get")
        except Exception as e:
            raise RuntimeError("Failed to load categorical attributes dictionary: {}".format(str(e.message)))

    def upsert_categorical_attribute(self, attribute):
        try:
            return self._request(
                endpoint=self.CATEGORICAL_ATTRIBUTE_URL, http_method="post", data=attribute
            )
        except Exception as e:
            raise RuntimeError("Failed to load categorical attributes dictionary: {}".format(str(e.message)))

    def grant_permissions(self, permissions_object):
        try:
            return self._request(
                endpoint=self.GRANT_PERMISSIONS_URL, http_method="post", data=permissions_object
            )
        except Exception as e:
            raise RuntimeError("Failed to grant permissions, object: {} error: {}".format(permissions_object, str(e.message)))

    def get_permissions(self, entity_id, entity_class):
        try:
            result = self._request(endpoint='grant?id={}&aclClass={}'
                                   .format(entity_id, entity_class), http_method="get")
            return result['permissions'] if 'permissions' in result else None
        except Exception as e:
            raise RuntimeError("Failed to load permissions, entity_id: {} error: {}".format(entity_id, str(e.message)))

    def terminate_run(self, run_id):
        try:
            return self._request(endpoint=self.TERMINATE_RUN.format(str(run_id)), http_method="post")
        except Exception as e:
            raise RuntimeError("Failed to terminate run. \n {}".format(e))

    def data_storage_load_all(self):
        try:
            return self._request(endpoint=self.DATA_STORAGE_LOAD_ALL_URL, http_method="get")
        except Exception as e:
            raise RuntimeError("Failed to load data storages. \n {}".format(e))

    def load_pipelines_by_owners(self, owners, statuses):
        try:
            data = {'page': '1', 'pageSize': self.MAX_PAGE_SIZE, 'owners': owners, 'statuses': statuses}
            result = self._request(endpoint=self.FILTER_RUNS, http_method="post", data=data)
            return result['elements'] if 'elements' in result else []
        except Exception as e:
            raise RuntimeError("Failed to load pipelines \n {}".format(e))

    def delete_tool(self, image):
        try:
            return self._request(endpoint='tool/delete?image={}'.format(image), http_method="delete")
        except Exception as e:
            raise RuntimeError("Failed to delete tool \n {}".format(e))

    def load_datastorage_items(self, storage_id):
        try:
            return self._request(endpoint=self.DATA_STORAGE_LIST_ITEMS_URL.format(id=storage_id), http_method="get")
        except Exception as e:
            raise RuntimeError("Failed to load datastorage items for storage id '{}'.".format(storage_id))

    def delete_user_home_storage(self, user_id):
        try:
            return self._request(endpoint='user/{}'.format(str(user_id)), http_method="put", data={})
        except Exception as e:
            raise RuntimeError("Failed to delete user home storage '{}'.".format(user_id))

    def stop_run(self, run_id):
        try:
            data = {'status': 'STOPPED', 'endDate': datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S.%f')}
            return self._request(endpoint='run/{}/status'.format(str(run_id)), http_method="post", data=data)
        except Exception as e:
            raise RuntimeError("Failed to stop run. \n {}".format(e))

    def get_entity_permissions(self, entity_id, entity_class):
        request_url = '%s?id=%s&aclClass=%s' % (self.PERMISSION_URL, str(entity_id), entity_class)
        try:
            return self._request(endpoint=request_url, http_method="get")
        except Exception as e:
            raise RuntimeError("Failed to load permissions for entity '{}' with ID '{}', error: {}".format(
                entity_class, str(entity_id), str(e)))

    def update_pipeline_run_tags(self, run_id, tags, keep_existing_tags=False):
        try:
            if 'tags' in tags:
                tags = tags['tags']

            if keep_existing_tags:
                run = self.load_run(run_id)
                current_tags = run.get('tags') or {}
                tags.update(current_tags)

            return self._request(endpoint=self.RUN_TAG.format(id=str(run_id)), http_method='post', data={ 'tags': tags })
        except Exception as e:
            raise RuntimeError("Failed to update tags for run ID '{}', error: {}".format(str(run_id), str(e)))

    def report_users(self, start, end, users):
        try:
            data = {'from': start, 'to': end, 'users': users, 'interval': 'HOURS'}
            return self._request(endpoint=self.REPORT_USERS, http_method="post", data=data)
        except Exception as e:
            raise RuntimeError("Failed to load users report \n {}".format(e))

    def log_group(self, filter, group_by):
        try:
            data = {'filter': filter, 'groupBy': group_by}
            return self._request(endpoint=self.LOG_GROUP, http_method="post", data=data)
        except Exception as e:
            raise RuntimeError("Failed to load logs \n {}".format(e))

    def load_storage_requests(self, body):
        try:
            return self._request(endpoint=self.STORAGE_REQUESTS, http_method="post", data=body)
        except Exception as e:
            raise RuntimeError("Failed to fetch storage requests data \n {}".format(e))

    def filter_runs(self, start, end, user, filter, page, page_size):
        try:
            data = {'owners': [user], 'startDateFrom': start, 'endDateTo': end, "page": page, "pageSize": page_size}
            if filter is not None:
                for key, value in filter.items():
                    data[key] = value
            result = self._request(endpoint=self.FILTER_RUNS, http_method="post", data=data)
            elements = result['elements'] if 'elements' in result else []
            total_count = result['totalCount'] if 'totalCount' in result else 0
            return elements, total_count
        except Exception as e:
            raise RuntimeError("Failed to load master runs \n {}".format(e))

    def filter_runs_all(self, start, end, user, filter):
        total_count = 0
        page = 0
        page_size = 100
        result = []
        while page == 0 or page * page_size < total_count:
            page += 1
            elements, total_count = self.filter_runs(start, end, user, filter, page, page_size)
            result.extend(elements)
        return result

    def run_count(self, start, end, user, filter):
        try:
            data = {'owners': [user], 'startDateFrom': start, 'endDateTo': end}
            if filter is not None:
                for key, value in filter.items():
                    data[key] = value
            return self._request(endpoint=self.RUN_COUNT, http_method="post", data=data)
        except Exception as e:
            raise RuntimeError("Failed to load runs count \n {}".format(e))

    def billing_export(self, start, end, filters, types):
        try:
            url = '{}/{}'.format(self.api_url, self.BILLING_EXPORT)
            data = {"types": types, "from": start, "to": end, "filters": filters, "discount": {"computes": 0, "storages": 0}}
            response = requests.request(method="post", url=url, data=json.dumps(data),
                                        headers=self.header, verify=False,
                                        timeout=self.connection_timeout)
            if response.status_code != 200:
                raise HTTPError('API responded with http status %s.' % str(response.status_code))
            return response.content
        except Exception as e:
            raise RuntimeError("Failed to load billing export \n {}".format(e))
