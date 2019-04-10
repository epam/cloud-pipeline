from google.cloud.storage import Client
from tzlocal import get_localzone

from buckets.utils.cloud.cloud_client import CloudClient


class GsClient(CloudClient):

    name = 'GS'

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
        blob = self._get_client().bucket(bucket).blob(key)
        self._reload_blob_generation(blob, version)
        return blob.metadata

    def _reload_blob_generation(self, blob, version=None):
        client = blob.bucket._client
        query_params = { "projection": "noAcl" }
        if version:
            query_params["generation"] = int(version)
        api_response = client._connection.api_request(
            method="GET", path=blob.path, query_params=query_params, _target_object=blob
        )
        blob._set_properties(api_response)

    def assert_policy(self, bucket_name, sts, lts, backup_duration):
        # TODO 29.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')

    def get_modification_date(self, path):
        path_elements = path.replace('cp://', '').split('/')
        bucket_name = path_elements[0]
        blob_name = '/'.join(path_elements[1:])
        blob = self._get_client().bucket(bucket_name).blob(blob_name)
        blob.reload()
        last_modified = blob.updated
        return last_modified.astimezone(get_localzone()).replace(tzinfo=None, microsecond=0)

    def get_versions(self, bucket_name, key):
        bucket_entries = self._get_bucket_entries(bucket_name, key, versions=True)
        bucket_entries.reverse()
        generations = []
        if not bucket_entries:
            return generations
        if bucket_entries[0].time_deleted:
            generations.append('-')
        for entry in bucket_entries:
            generations.append(str(entry.generation))
        return generations

    def wait_for_bucket_creation(self, bucket_name):
        if self._wait_unless(lambda: self._get_client().bucket(bucket_name).exists()):
            return
        raise RuntimeError('Google storage with name=%s wasn\'t created.' % bucket_name)

    def wait_for_bucket_deletion(self, bucket_name):
        if self._wait_unless(lambda: not self._get_client().bucket(bucket_name).exists()):
            return
        raise RuntimeError('Google storage with name=%s wasn\'t deleted.' % bucket_name)

    def _get_bucket_entries(self, bucket_name, key, versions=False):
        return list(self._get_client().get_bucket(bucket_name).list_blobs(prefix=key, versions=versions))

    def _get_client(self):
        return Client()
