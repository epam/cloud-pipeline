CREATE OR REPLACE FUNCTION update_pipeline_event_on_tool() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_OP = 'DELETE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'tool', OLD.id;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'tool', OLD.id;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'tool', NEW.id;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_tool
AFTER INSERT OR UPDATE OR DELETE ON pipeline.tool FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_tool();
