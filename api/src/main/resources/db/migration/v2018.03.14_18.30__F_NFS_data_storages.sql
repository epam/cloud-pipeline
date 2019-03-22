-- insert a new class to acl_class table and modify exisitng due to refactoring
INSERT INTO pipeline.acl_class (class) VALUES ('com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage');
-- mount_point will contain an optional path to directory to mount this data storage to
ALTER TABLE pipeline.datastorage ADD COLUMN mount_point TEXT;
