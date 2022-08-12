class TestCase:

    def __init__(self, cloud, storages, result):
        self.cloud = cloud
        self.storages = storages
        self.result = result


class TestCaseStorageDescription:

    def __init__(self):
        self.storage = None
        self.rule = None
        self.files = []

    def with_storage_name(self, storage):
        self.storage = storage
        return self

    def with_rule(self, rule):
        self.rule = rule
        return self

    def with_file(self, file):
        self.files.append(file)
        return self


class TestCaseFile:

    def __init__(self, path, storage_date_shift, storage_class, tags):
        self.path = path
        self.storage_date_shift = storage_date_shift
        self.storage_class = storage_class
        self.tags = tags


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
            self.cloud_state = to_merge.cloud_state
        if to_merge.platform_state:
            self.platform_state = to_merge.platform_state
        return self


class TestCaseCloudState:

    def __init__(self, storages=None):
        self.storages = storages


class TestCasePlatformState:

    def __init__(self, notifications=None, executions=None):
        self.notifications = notifications
        self.executions = executions
