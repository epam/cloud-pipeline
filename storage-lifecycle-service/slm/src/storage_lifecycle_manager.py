from slm.src.logger import AppLogger
from slm.src.synchronizer.lifecycle_syncronizer import UnsupportedStorageLifecycleSynchronizer, \
    S3StorageLifecycleSynchronizer


class StorageLifecycleManager:

    def __init__(self, config, cp_data_source, logger=AppLogger()):
        self.logger = logger
        self.api = cp_data_source
        self.synchronizers = {
            "S3": S3StorageLifecycleSynchronizer(config, cp_data_source, logger=logger),
            "GC": UnsupportedStorageLifecycleSynchronizer(cp_data_source, logger=logger),
            "AZ": UnsupportedStorageLifecycleSynchronizer(cp_data_source, logger=logger),
            "NFS": UnsupportedStorageLifecycleSynchronizer(cp_data_source, logger=logger)
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
