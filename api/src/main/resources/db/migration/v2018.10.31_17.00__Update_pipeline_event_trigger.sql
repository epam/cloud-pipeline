CREATE OR REPLACE FUNCTION update_pipeline_event() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_OP = 'DELETE') THEN
            IF (TG_NAME = 't_pipeline_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'run', OLD.run_id;
            ELSIF (TG_NAME = 't_pipeline_run_log') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.run_id;
            ELSIF (TG_NAME = 't_restart_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.parent_run_id;
            ELSIF (TG_NAME = 't_run_status') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.run_id;
            END IF;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            IF (TG_NAME = 't_pipeline_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.run_id;
            ELSIF (TG_NAME = 't_pipeline_run_log') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.run_id;
            ELSIF (TG_NAME = 't_restart_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.parent_run_id;
            ELSIF (TG_NAME = 't_run_status') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', OLD.run_id;
            END IF;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            IF (TG_NAME = 't_pipeline_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'run', NEW.run_id;
            ELSIF (TG_NAME = 't_pipeline_run_log') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', NEW.run_id;
            ELSIF (TG_NAME = 't_restart_run') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', NEW.parent_run_id;
            ELSIF (TG_NAME = 't_run_status') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'run', NEW.run_id;
            END IF;
            RETURN NEW;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;