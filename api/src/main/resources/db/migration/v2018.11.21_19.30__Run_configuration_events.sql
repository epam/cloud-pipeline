
CREATE OR REPLACE FUNCTION update_run_configuration_events() RETURNS TRIGGER AS $PIPELINE_EVENT$
BEGIN
  IF (TG_OP = 'DELETE') THEN
    INSERT INTO PIPELINE_EVENT SELECT 'D', now(), 'configuration', OLD.id;
    RETURN OLD;
  ELSIF (TG_OP = 'UPDATE') THEN
    INSERT INTO PIPELINE_EVENT SELECT 'U', now(), 'configuration', OLD.id;
    RETURN NEW;
  ELSIF (TG_OP = 'INSERT') THEN
    INSERT INTO PIPELINE_EVENT SELECT 'I', now(), 'configuration', NEW.id;
    RETURN NEW;
  END IF;
  RETURN NULL;
END;
$PIPELINE_EVENT$ LANGUAGE plpgsql;

CREATE TRIGGER t_configuration
  AFTER INSERT OR UPDATE OR DELETE ON pipeline.configuration FOR EACH ROW
EXECUTE PROCEDURE update_run_configuration_events();
