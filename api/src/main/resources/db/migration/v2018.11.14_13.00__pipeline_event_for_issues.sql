CREATE OR REPLACE FUNCTION update_pipeline_event_on_issues_and_comments() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
        IF (TG_NAME = 't_issue') THEN
            IF (TG_OP = 'DELETE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'issue', OLD.issue_id;
                RETURN OLD;
            ELSIF (TG_OP = 'UPDATE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'issue', OLD.issue_id;
                RETURN NEW;
            ELSIF (TG_OP = 'INSERT') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'issue', NEW.issue_id;
                RETURN NEW;
            END IF;
        ELSIF (TG_NAME = 't_issue_comment') THEN
            IF (TG_OP = 'DELETE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'issue', OLD.issue_id;
                RETURN OLD;
            ELSIF (TG_OP = 'UPDATE') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'issue', OLD.issue_id;
                RETURN NEW;
            ELSIF (TG_OP = 'INSERT') THEN
                INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'issue', NEW.issue_id;
                RETURN NEW;
            END IF;
        END IF;
        RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_issue
AFTER INSERT OR UPDATE OR DELETE ON pipeline.issue FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_issues_and_comments();

CREATE TRIGGER t_issue_comment
AFTER INSERT OR UPDATE OR DELETE ON pipeline.issue_comment FOR EACH ROW
EXECUTE PROCEDURE update_pipeline_event_on_issues_and_comments();
