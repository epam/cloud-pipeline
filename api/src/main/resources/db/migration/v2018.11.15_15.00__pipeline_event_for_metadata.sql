CREATE OR REPLACE FUNCTION update_pipeline_event_on_metadata() RETURNS TRIGGER AS $PIPELINE_EVENT$
DECLARE
    ds_type TEXT;
BEGIN
        IF (TG_OP = 'DELETE') THEN
            IF (OLD.entity_class = 'DATA_STORAGE') THEN
                ds_type = (SELECT datastorage_type FROM pipeline.datastorage WHERE datastorage_id = OLD.entity_id);
                IF (ds_type IS NOT NULL) THEN
                    INSERT INTO PIPELINE_EVENT SELECT 'U', now(), ds_type, OLD.entity_id;
                END IF;
            ELSE
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), lower(OLD.entity_class), OLD.entity_id;
            END IF;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            IF (OLD.entity_class = 'DATA_STORAGE') THEN
                ds_type = (SELECT datastorage_type FROM pipeline.datastorage WHERE datastorage_id = OLD.entity_id);
                IF (ds_type IS NOT NULL) THEN
                    INSERT INTO PIPELINE_EVENT SELECT 'U', now(), ds_type, OLD.entity_id;
                END IF;
            ELSE
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), lower(OLD.entity_class), OLD.entity_id;
            END IF;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
           IF (NEW.entity_class = 'DATA_STORAGE') THEN
                ds_type = (SELECT datastorage_type FROM pipeline.datastorage WHERE datastorage_id = NEW.entity_id);
                IF (ds_type IS NOT NULL) THEN
                    INSERT INTO PIPELINE_EVENT SELECT 'U', now(), ds_type, NEW.entity_id;
                END IF;
            ELSE
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), lower(NEW.entity_class), NEW.entity_id;
            END IF;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_metadata
AFTER INSERT OR UPDATE OR DELETE ON pipeline.metadata FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_metadata();
