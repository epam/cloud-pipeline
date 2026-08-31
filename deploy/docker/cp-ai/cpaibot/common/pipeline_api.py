import asyncio
import os
import json
import time
import datetime

import httpx
import urllib3
import requests


# enumeration with task statuses, supported by Pipeline API
class TaskStatus:
    SUCCESS, FAILURE, RUNNING, STOPPED, PAUSED = range(5)


class Token:

    def get(self):
        pass

class StaticToken(Token):

    def __init__(self, value=None):
        self._value = value or os.environ['API_TOKEN']

    def get(self):
        return self._value


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


# Date format expected by Pipeline API
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
# date format for filename generation
FILE_DATE_FORMAT = "%Y%m%d"


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
    METADATA_ENTITY_FIELDS = '/metadataEntity/fields'
    SAVE_METADATA_ENTITY = "metadataEntity/save"
    FIND_METADATA_ENTITY = "metadataEntity/loadExternal?id=%s&folderId=%d&className=%s"
    LOAD_ENTITIES_DATA = "/metadataEntity/entities"
    METADATA_ENTITY_FILTER = '/metadataEntity/filter'
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
    DATA_STORAGE_MOUNT_LOAD = '/filesharemount/{id}'
    RUN_ENGINE_EVENTS_URL = '/run/{id}/engine/tasks'
    RUN_RESULT_URL = '/run/{id}/result'


    # Pipeline API default header

    RESPONSE_STATUS_OK = 'OK'
    MAX_PAGE_SIZE = 400

    def __init__(self, api_url=None, log_dir=None, attempts=3, timeout=5, connection_timeout=10, token=None):
        urllib3.disable_warnings()
        self.api_url = api_url or os.environ['API']
        self.log_dir = log_dir or os.getenv('LOG_DIR', '/var/log')
        self.attempts = attempts
        self.timeout = timeout
        self.connection_timeout = connection_timeout
        self.token = token or StaticToken()

    @property
    def header(self):
        return {'content-type': 'application/json',
                'Authorization': 'Bearer {}'.format(self.token.get())}

    def _request(self, http_method, endpoint, data=None, params=None):
        url = '{}/{}'.format(self.api_url, endpoint)
        count = 0
        exceptions = []
        while count < self.attempts:
            count += 1
            try:
                response = requests.request(
                    method=http_method,
                    url=url,
                    data=json.dumps(data) if data else None,
                    params=params,
                    headers=self.header,
                    verify=False,
                    timeout=self.connection_timeout
                )
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


    async def _request_async(self, http_method, endpoint, data=None, params=None):
        url = '{}/{}'.format(self.api_url, endpoint)
        count = 0
        exceptions = []
        async with httpx.AsyncClient(verify=False, timeout=self.connection_timeout) as client:
            while count < self.attempts:
                count += 1
                try:
                    response = await client.request(
                        method=http_method,
                        url=url,
                        json=data,
                        params=params,
                        headers=self.header,
                    )
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
                await asyncio.sleep(self.timeout)
        raise exceptions[-1]


    def load_categorical_attributes_dictionary(self):
        try:
            return self._request(endpoint=self.CATEGORICAL_ATTRIBUTE_URL, http_method="get")
        except Exception as e:
            raise RuntimeError("Failed to load categorical attributes dictionary: {}".format(e.__str__()))

    def upsert_categorical_attribute(self, attribute):
        try:
            return self._request(
                endpoint=self.CATEGORICAL_ATTRIBUTE_URL, http_method="post", data=attribute
            )
        except Exception as e:
            raise RuntimeError("Failed to load categorical attributes dictionary: {}".format(e.__str__()))

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
                if self.api_url and log_entry.runId:
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
                print("Failed to save task log: " + e.__str__())


    def get_metadata_entity_fields(self, folder_id: int) -> dict:
        """
        Fetch metadata entity fields for a folder.
        """
        try:
            return self._request(endpoint=self.METADATA_ENTITY_FIELDS, http_method="get", params={"folderId": folder_id})
        except Exception as e:
            raise RuntimeError(f"Failed to fetch metadata entity fields for folder ID {folder_id}: {e}")

    async def get_metadata_entity_fields_async(self, folder_id: int) -> dict:
        """
        Fetch metadata entity fields for a folder asynchronously.
        """
        try:
            return await self._request_async(endpoint=self.METADATA_ENTITY_FIELDS, http_method="get", params={"folderId": folder_id})
        except Exception as e:
            raise RuntimeError(f"Failed to fetch metadata entity fields for folder ID {folder_id}: {e}")

    def filter_metadata_entity(self, filter_criteria: dict) -> dict:
        """
        Filter metadata entities based on criteria.
        """
        try:
            return self._request(endpoint=self.METADATA_ENTITY_FILTER, http_method="post", data=filter_criteria)
        except Exception as e:
            raise RuntimeError(f"Failed to filter metadata entities: {e}")

    async def filter_metadata_entity_async(self, filter_criteria: dict) -> dict:
        """
        Filter metadata entities based on criteria asynchronously.
        """
        try:
            return await self._request_async(endpoint=self.METADATA_ENTITY_FILTER, http_method="post", data=filter_criteria)
        except Exception as e:
            raise RuntimeError(f"Failed to filter metadata entities: {e}")


class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

    STATUS_COLORS = {TaskStatus.RUNNING: '',
                     TaskStatus.SUCCESS: OKGREEN,
                     TaskStatus.FAILURE: FAIL}

    @staticmethod
    def colored(message, status):
        return bcolors.STATUS_COLORS[status] + message + bcolors.ENDC


class Logger:

    @staticmethod
    def info(message,
             task_name=None,
             run_id=None,
             api_url=None,
             log_dir=None,
             omit_console=False):

        Logger.log_task_event(task_name,
                              message,
                              status=TaskStatus.RUNNING,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)


    @staticmethod
    def warn(message,
             task_name=None,
             run_id=None,
             api_url=None,
             log_dir=None,
             omit_console=False):

        Logger.log_task_event(task_name,
                              bcolors.WARNING + message + bcolors.ENDC,
                              status=TaskStatus.RUNNING,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)


    @staticmethod
    def success(message,
                task_name=None,
                run_id=None,
                api_url=None,
                log_dir=None,
                omit_console=False):

        Logger.log_task_event(task_name,
                              message,
                              status=TaskStatus.SUCCESS,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)


    @staticmethod
    def fail(message,
             task_name=None,
             run_id=None,
             api_url=None,
             log_dir=None,
             omit_console=False):

        Logger.log_task_event(task_name,
                              message,
                              status=TaskStatus.FAILURE,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)



    @staticmethod
    def log_task_event(task_name, message, status=TaskStatus.RUNNING, run_id=None, instance=None, api_url=None, log_dir=None, omit_console=False):
        _run_id = run_id
        _instance = instance
        _api_url = api_url
        _log_dir = log_dir
        _task_name = task_name
        _pipeline_name = os.environ.get('PIPELINE_NAME')

        if not _pipeline_name:
            _pipeline_name = 'Pipeline-output'

        if not _task_name:
            _task_name = _pipeline_name

        if not _run_id:
            _run_id = os.environ.get('RUN_ID')

        if not _instance:
            pipeline_name = _pipeline_name
            _instance = "{}-{}".format(pipeline_name, _run_id)

        if not _api_url:
            _api_url = os.environ.get('API')

        if not _log_dir:
            _log_dir = os.environ.get('LOG_DIR')

        log_entry = LogEntry(_run_id,
                             status,
                             bcolors.colored(message, status),
                             _task_name,
                             instance)
        pipe_api = PipelineAPI(_api_url, _log_dir)
        pipe_api.log_event(log_entry, omit_console=omit_console)
