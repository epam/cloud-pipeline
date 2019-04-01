from abc import ABCMeta, abstractmethod
from time import sleep


class CloudClient(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def object_exists(self, bucket_name, key):
        pass

    @abstractmethod
    def folder_exists(self, bucket_name, key):
        pass

    @abstractmethod
    def list_object_tags(self, bucket, key, version=None, args=None):
        pass

    @abstractmethod
    def assert_policy(self, bucket_name, sts, lts, backup_duration):
        pass

    @abstractmethod
    def get_modification_date(self, path):
        pass

    @abstractmethod
    def get_versions(self, path):
        pass

    @abstractmethod
    def wait_for_bucket_creation(self, bucket_name):
        pass

    @abstractmethod
    def wait_for_bucket_deletion(self, bucket_name):
        pass

    def _wait_unless(self, is_ready, attempts=20, delay=3):
        for attempt in range(attempts):
            if is_ready():
                return True
            sleep(delay)
        return False
