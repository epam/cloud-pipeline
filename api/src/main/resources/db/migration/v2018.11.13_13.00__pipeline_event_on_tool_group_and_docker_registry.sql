CREATE OR REPLACE FUNCTION update_pipeline_event_on_tool_group_and_registry() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_NAME = 't_tool_group') THEN
            IF (TG_OP = 'DELETE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'tool_group', OLD.id;
                RETURN OLD;
            ELSIF (TG_OP = 'UPDATE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'tool_group', OLD.id;
                RETURN NEW;
            ELSIF (TG_OP = 'INSERT') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'tool_group', NEW.id;
                RETURN NEW;
            END IF;
        ELSIF (TG_NAME = 't_docker_registry') THEN
            IF (TG_OP = 'DELETE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'docker_registry', OLD.id;
                RETURN OLD;
            ELSIF (TG_OP = 'UPDATE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'docker_registry', OLD.id;
                RETURN NEW;
            ELSIF (TG_OP = 'INSERT') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'docker_registry', NEW.id;
                RETURN NEW;
            END IF;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_tool_group
AFTER INSERT OR UPDATE OR DELETE ON pipeline.tool_group FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_tool_group_and_registry();

CREATE TRIGGER t_docker_registry
AFTER INSERT OR UPDATE OR DELETE ON pipeline.docker_registry FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_tool_group_and_registry();
