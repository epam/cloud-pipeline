from slm.src.logger import AppLogger
from slm.src.synchronizer.lifecycle_syncronizer import UnsupportedStorageLifecycleSynchronizer, \
    S3StorageLifecycleSynchronizer


class StorageLifecycleManager:

    def __init__(self, api,  logger=AppLogger()):
        self.logger = logger
        self.api = api
        self.synchronizers = {
            "S3": S3StorageLifecycleSynchronizer(api, logger=logger),
            "GC": UnsupportedStorageLifecycleSynchronizer(api, logger=logger),
            "AZ": UnsupportedStorageLifecycleSynchronizer(api, logger=logger),
            "NFS": UnsupportedStorageLifecycleSynchronizer(api, logger=logger)
        }

    def sync(self):
        self.logger.log("Starting object lifecycle synchronization process...")
        available_storages = self.api.load_available_storages()
        self.logger.log("{} storages loaded.".format(len(available_storages)))
        for storage in available_storages:
            self.logger.log(
                "Starting object lifecycle synchronization process for {} with type {}.".format(
                    storage.path, storage.type)
            )
            self.synchronizers.get(storage.type).sync_storage(storage)
            self.logger.log(
                "Finish object lifecycle synchronization process for {} with type {}.".format(
                    storage.path, storage.type)
            )
        self.logger.log("Done object lifecycle synchronization process...")
