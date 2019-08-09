from time import sleep

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
        sleep(5)
        bucket = self._get_client().bucket(bucket_name)
        bucket.reload()
        actual_policy = {"backup_duration": None, "lts": None, "sts": None}
        for rule in bucket.lifecycle_rules:
            assert 'action' in rule and 'condition' in rule
            condition = rule['condition']
            rule_type = rule['action']['type']
            duration = condition['age']
            if rule_type == 'Delete' and condition['isLive']:
                actual_policy['lts'] = duration
            if rule_type == 'SetStorageClass':
                actual_policy['sts'] = duration
            if rule_type == 'Delete' and not condition['isLive']:
                actual_policy['backup_duration'] = duration
                assert int(duration) == int(backup_duration), \
                    "Backup Duration assertion failed: expected %s but actual %s" % (backup_duration, duration)
        assert str(actual_policy['lts']) == str(lts), \
            "LTS assertion failed: expected %s but actual %s" % (lts, actual_policy['lts'])
        assert str(actual_policy['sts']) == str(sts), \
            "STS assertion failed: expected %s but actual %s" % (sts, actual_policy['sts'])
        assert str(actual_policy['backup_duration']) == str(backup_duration), \
            "Backup Duration assertion failed: expected %s but actual %s" % (backup_duration,
                                                                             actual_policy['backup_duration'])

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
