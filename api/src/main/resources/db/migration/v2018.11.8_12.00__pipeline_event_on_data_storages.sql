CREATE OR REPLACE FUNCTION update_pipeline_event_on_data_storage() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_OP = 'DELETE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'D', now(), OLD.datastorage_type, OLD.datastorage_id;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'U', now(), OLD.datastorage_type, OLD.datastorage_id;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'I', now(), NEW.datastorage_type, NEW.datastorage_id;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_datastorage
AFTER INSERT OR UPDATE OR DELETE ON pipeline.datastorage FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_data_storage();
