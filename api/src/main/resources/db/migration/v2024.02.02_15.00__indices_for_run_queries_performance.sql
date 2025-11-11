CREATE INDEX IF NOT EXISTS run_parent_id_index ON pipeline.pipeline_run (parent_id);
CREATE INDEX IF NOT EXISTS run_status_index ON pipeline.pipeline_run (status);

CREATE INDEX IF NOT EXISTS log_task_name_index ON pipeline.pipeline_run_log (task_name);
CREATE INDEX IF NOT EXISTS log_task_name_run_id_index ON pipeline.pipeline_run_log (run_id, task_name);
CREATE INDEX IF NOT EXISTS log_task_name_run_id_status_index ON pipeline.pipeline_run_log (run_id, task_name, status);