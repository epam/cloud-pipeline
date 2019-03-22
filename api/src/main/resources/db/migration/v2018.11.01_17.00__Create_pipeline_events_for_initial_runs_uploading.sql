CREATE OR REPLACE FUNCTION create_pipeline_events_for_initial_runs_by_date(created_date TIMESTAMP WITH TIME ZONE) RETURNS void AS $$
BEGIN
  INSERT INTO PIPELINE.PIPELINE_EVENT (operation, stamp, object_type, object_id)
  SELECT 'I', now(), 'run', run_id FROM PIPELINE.PIPELINE_RUN AS runs WHERE start_date = created_date;
END;
$$ LANGUAGE plpgsql;