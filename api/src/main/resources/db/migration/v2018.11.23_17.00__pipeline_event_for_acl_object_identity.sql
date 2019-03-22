CREATE OR REPLACE FUNCTION upd_pipeline_events(object_id BIGINT, entity_class VARCHAR(100)) RETURNS void AS $$
DECLARE
    ds_type TEXT;
BEGIN
    IF (entity_class = 'S3bucketDataStorage') THEN
        INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'S3', object_id;
    ELSIF (entity_class = 'NFSDataStorage') THEN
        INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'NFS', object_id;
    ELSIF (entity_class = 'AbstractDataStorage') THEN
        ds_type = (SELECT datastorage_type FROM pipeline.datastorage WHERE datastorage_id = object_id);
        IF (ds_type IS NOT NULL) THEN
            INSERT INTO PIPELINE_EVENT SELECT 'U', now(), ds_type, object_id;
        END IF;
    ELSE
        INSERT INTO PIPELINE_EVENT SELECT 'U', now(), lower(entity_class), object_id;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_pipeline_event_on_acl_entry() RETURNS TRIGGER AS $PIPELINE_EVENT$
DECLARE
    entity_class VARCHAR(100);
    object_id BIGINT;
BEGIN
        IF (TG_OP = 'DELETE') THEN
            entity_class = (SELECT substring(class, '[^.]*$') FROM pipeline.acl_class ac RIGHT JOIN pipeline.acl_object_identity aoi ON ac.id = aoi.object_id_class WHERE aoi.id = OLD.acl_object_identity);
            IF (entity_class IS NOT NULL) THEN
                object_id = (SELECT object_id_identity FROM pipeline.acl_object_identity aoi WHERE aoi.id = OLD.acl_object_identity);
                PERFORM upd_pipeline_events(object_id, entity_class);
            END IF;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            entity_class = (SELECT substring(class, '[^.]*$') FROM pipeline.acl_class ac RIGHT JOIN pipeline.acl_object_identity aoi ON ac.id = aoi.object_id_class WHERE aoi.id = OLD.acl_object_identity);
            IF (entity_class IS NOT NULL) THEN
                object_id = (SELECT object_id_identity FROM pipeline.acl_object_identity aoi WHERE aoi.id = OLD.acl_object_identity);
                PERFORM upd_pipeline_events(object_id, entity_class);
            END IF;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            entity_class = (SELECT substring(class, '[^.]*$') FROM pipeline.acl_class ac RIGHT JOIN pipeline.acl_object_identity aoi ON ac.id = aoi.object_id_class WHERE aoi.id = NEW.acl_object_identity);
            IF (entity_class IS NOT NULL) THEN
                object_id = (SELECT object_id_identity FROM pipeline.acl_object_identity aoi WHERE aoi.id = NEW.acl_object_identity);
                PERFORM upd_pipeline_events(object_id, entity_class);
            END IF;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_acl_entry
AFTER INSERT OR UPDATE OR DELETE ON pipeline.acl_entry FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_acl_entry();
