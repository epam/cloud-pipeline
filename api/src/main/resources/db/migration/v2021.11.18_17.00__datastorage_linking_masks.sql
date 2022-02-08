ALTER TABLE pipeline.datastorage ADD source_datastorage_id BIGINT DEFAULT NULL;
ALTER TABLE pipeline.datastorage ADD masking_rules JSONB DEFAULT NULL;
ALTER TABLE pipeline.datastorage
    ADD CONSTRAINT datastorage_linked_storage_id_fk FOREIGN KEY (source_datastorage_id)
        REFERENCES pipeline.datastorage(datastorage_id) ON DELETE CASCADE;
