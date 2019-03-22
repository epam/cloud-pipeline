CREATE OR REPLACE FUNCTION update_pipeline_events() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_OP = 'DELETE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'pipeline', OLD.pipeline_id;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'pipeline', OLD.pipeline_id;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'pipeline', NEW.pipeline_id;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_pipeline
AFTER INSERT OR UPDATE OR DELETE ON pipeline.pipeline FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_events();