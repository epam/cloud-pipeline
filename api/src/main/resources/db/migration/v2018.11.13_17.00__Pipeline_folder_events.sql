CREATE OR REPLACE FUNCTION update_pipeline_folder_events() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_OP = 'DELETE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'folder', OLD.folder_id;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'folder', OLD.folder_id;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'folder', NEW.folder_id;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_folder
AFTER INSERT OR UPDATE OR DELETE ON pipeline.folder FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_folder_events();