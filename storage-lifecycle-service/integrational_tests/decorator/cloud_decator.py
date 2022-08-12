import datetime

from sls.cloud.cloud import StorageOperations


class AttributesChangingStorageOperations(StorageOperations):

    def __init__(self, cloud_operations, testcase):
        self.cloud_operations = cloud_operations
        self.watched_files_by_storages = {}
        for storage in testcase.storages:
            self.watched_files_by_storages[storage.storage] = {}
            for file in storage.files:
                self.watched_files_by_storages[storage.storage][file.path] = file

    def prepare_bucket_if_needed(self, bucket):
        self.cloud_operations.prepare_bucket_if_needed(bucket)

    def list_objects_by_prefix(self, bucket, glob_str):
        intermediate_result = self.cloud_operations.list_objects_by_prefix(bucket, glob_str)
        for file in intermediate_result:
            if file.path in self.watched_files_by_storages[bucket]:
                file.storage_class = self.watched_files_by_storages[bucket][file.path].storage_class
                file.creation_date = file.creation_date - datetime.timedelta(
                    days=self.watched_files_by_storages[bucket][file.path].storage_date_shift)
        return intermediate_result

    def process_files_on_cloud(self, bucket, region, rule, folder, storage_class, files):
        return self.cloud_operations.process_files_on_cloud(bucket, region, rule, folder, storage_class, files)
