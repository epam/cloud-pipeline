import os
from time import sleep

from azure.storage.blob import BlockBlobService
from tzlocal import get_localzone

from buckets.utils.cloud.cloud_client import CloudClient

ACCOUNT_NAME = os.environ['AZURE_STORAGE_ACCOUNT']
ACCOUNT_KEY = os.environ['AZURE_ACCOUNT_KEY'] if 'AZURE_ACCOUNT_KEY' in os.environ else 'NO ACCOUNT KEY'


class AzureClient(CloudClient):

    name = "AZ"

    def object_exists(self, bucket_name, key):
        bucket_entries = self._get_bucket_entries(bucket_name, key)
        return len(bucket_entries) > 0 and any(entry.name == key for entry in bucket_entries)

    def folder_exists(self, bucket_name, key):
        return len(self._list_folder(bucket_name, key)) > 0

    def _list_folder(self, bucket_name, key):
        if not key.endswith('/'):
            key = key + '/'
        bucket_entry = self._get_bucket_entries(bucket_name, key)
        result = []
        for entry in bucket_entry:
            result.append(entry.name)
        return result

    def list_object_tags(self, bucket, key, version=None, args=None):
        return self._get_client().get_blob_metadata(bucket, key)

    def assert_policy(self, bucket_name, sts, lts, backup_duration):
        # TODO 15.02.19: Implement
        raise RuntimeError('Azure listing details assertion hasn\'t been implemented yet.')

    def get_modification_date(self, path):
        path_elements = path.replace('cp://', '').split('/')
        bucket_name = path_elements[0]
        blob_name = '/'.join(path_elements[1:])
        bucket_entries = self._get_bucket_entries(bucket_name, blob_name)
        last_modified = bucket_entries[0].properties.last_modified
        return last_modified.astimezone(get_localzone()).replace(tzinfo=None)

    def _get_bucket_entries(self, bucket_name, key):
        return list(self._get_client().list_blobs(bucket_name, key))

    def wait_for_bucket_creation(self, bucket_name):
        if self._wait_unless(lambda: self._get_client().exists(bucket_name)):
            return
        raise RuntimeError('Azure storage with name=%s wasn\'t created.' % bucket_name)

    def wait_for_bucket_deletion(self, bucket_name):
        if self._wait_unless(lambda: not self._get_client().exists(bucket_name)):
            return
        raise RuntimeError('Azure storage with name=%s wasn\'t deleted.' % bucket_name)

    def _wait_unless(self, is_ready, attempts=20, delay=3):
        for attempt in range(attempts):
            if is_ready():
                return True
            sleep(delay)
        return False

    def _get_client(self):
        return BlockBlobService(account_name=ACCOUNT_NAME, account_key=ACCOUNT_KEY)
