CREATE INDEX IF NOT EXISTS pipeline_run_log_task_name_status_index ON pipeline.pipeline_run_log (task_name, status);
