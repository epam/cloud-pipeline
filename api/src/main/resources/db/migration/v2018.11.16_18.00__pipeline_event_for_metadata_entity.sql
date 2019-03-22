CREATE OR REPLACE FUNCTION update_pipeline_event_on_metadata_entity() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_OP = 'DELETE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'metadata_entity', OLD.entity_id;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'metadata_entity', OLD.entity_id;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'metadata_entity', NEW.entity_id;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_metadata_entity
AFTER INSERT OR UPDATE OR DELETE ON pipeline.metadata_entity FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_metadata_entity();
