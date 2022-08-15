class TestCase:

    def __init__(self, cloud, platform, result):
        self.cloud = cloud
        self.platform = platform
        self.result = result


class TestCaseCloudState:

    def __init__(self, storages=None):
        self.storages = storages


class TestCasePlatformState:

    def __init__(self, storages=None):
        if storages is None:
            storages = []
        self.storages = storages


class TestCaseStorageCloudState:

    def __init__(self):
        self.cloud_provider = None
        self.storage = None
        self.files = []

    def with_storage_name(self, storage):
        self.storage = storage
        return self

    def with_file(self, file):
        self.files.append(file)
        return self

    def with_cloud_provider(self, cloud_provider):
        self.cloud_provider = cloud_provider
        return self


class TestCaseFile:

    def __init__(self, path, storage_date_shift, storage_class, tags):
        self.path = path
        self.storage_date_shift = storage_date_shift
        self.storage_class = storage_class
        self.tags = tags


class TestCasePlatformStorageState:

    def __init__(self, datastorage_id, storage, rules=None, notifications=None, executions=None):
        self.datastorage_id = datastorage_id
        self.storage = storage
        if rules is None:
            rules = []
        if notifications is None:
            notifications = []
        if executions is None:
            executions = []

        self.rules = rules
        self.notifications = notifications
        self.executions = executions

    def with_rule(self, rule):
        self.rules.append(rule)
        return self

    def with_notification(self, notification):
        self.notifications.append(notification)
        return self

    def with_execution(self, execution):
        self.executions.append(execution)
        return self


class TestCaseResult:

    def __init__(self):
        self.cloud_state = None
        self.platform_state = None

    def with_cloud_state(self, state):
        self.cloud_state = state
        return self

    def with_platform_state(self, state):
        self.platform_state = state
        return self

    def merge(self, to_merge):
        if not to_merge:
            return self
        if to_merge.cloud_state:
            if self.cloud_state:
                self.cloud_state.storages.append(
                    to_merge.cloud_state.storages if to_merge.cloud_state.storages else [])
            else:
                self.cloud_state = to_merge.cloud_state
        if to_merge.platform_state:
            if self.platform_state:
                self.platform_state.storages.append(
                    to_merge.platform_state.storages if to_merge.cloud_state.storages else [])
            else:
                self.platform_state = to_merge.platform_state
        return self
