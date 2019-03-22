ALTER TABLE pipeline."user" ADD default_storage_id bigint NULL;
ALTER TABLE pipeline."user" ADD CONSTRAINT user_datastorage_datastorage_id_fk FOREIGN KEY (default_storage_id) REFERENCES datastorage (datastorage_id);


ALTER TABLE pipeline.role ADD default_storage_id bigint NULL;
ALTER TABLE pipeline.role ADD CONSTRAINT role_datastorage_datastorage_id_fk FOREIGN KEY (default_storage_id) REFERENCES datastorage (datastorage_id);