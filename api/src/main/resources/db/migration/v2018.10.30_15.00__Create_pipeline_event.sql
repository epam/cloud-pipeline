CREATE TABLE IF NOT EXISTS PIPELINE.PIPELINE_EVENT (
	operation   VARCHAR(1) NOT NULL,
	stamp   TIMESTAMP WITH TIME ZONE NOT NULL,
	object_type TEXT    NOT NULL,
	object_id   BIGINT  NOT NULL
);

CREATE OR REPLACE FUNCTION update_pipeline_event() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_OP = 'DELETE') THEN
            IF (TG_NAME = 't_pipeline_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'run', OLD.run_id;
            ELSIF (TG_NAME = 't_pipeline_run_log') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'run_log', OLD.run_id;
            ELSIF (TG_NAME = 't_restart_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'restart_run', OLD.parent_run_id;
            ELSIF (TG_NAME = 't_run_status') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'run_status', OLD.run_id;
            END IF;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            IF (TG_NAME = 't_pipeline_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.run_id;
            ELSIF (TG_NAME = 't_pipeline_run_log') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run_log', OLD.run_id;
            ELSIF (TG_NAME = 't_restart_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'restart_run', OLD.parent_run_id;
            ELSIF (TG_NAME = 't_run_status') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run_status', OLD.run_id;
            END IF;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            IF (TG_NAME = 't_pipeline_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'run', NEW.run_id;
            ELSIF (TG_NAME = 't_pipeline_run_log') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'run_log', NEW.run_id;
            ELSIF (TG_NAME = 't_restart_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'restart_run', NEW.parent_run_id;
            ELSIF (TG_NAME = 't_run_status') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'run_status', NEW.run_id;
            END IF;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_pipeline_run
AFTER INSERT OR UPDATE OR DELETE ON pipeline.pipeline_run FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event();

CREATE TRIGGER t_pipeline_run_log
AFTER INSERT OR UPDATE OR DELETE ON pipeline.pipeline_run_log FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event();

CREATE TRIGGER t_restart_run
AFTER INSERT OR UPDATE OR DELETE ON pipeline.restart_run FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event();

CREATE TRIGGER t_run_status
AFTER INSERT OR UPDATE OR DELETE ON pipeline.run_status_change FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event();