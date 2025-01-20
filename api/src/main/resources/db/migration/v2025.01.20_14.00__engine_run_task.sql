CREATE TABLE IF NOT EXISTS pipeline.engine_run_task (
	task_id         TEXT        NOT NULL,
	task_name       TEXT,
	task_key        TEXT,
	task_group      TEXT,
	parent_id       TEXT,
	engine_type     TEXT        NOT NULL,
	status          TEXT        NOT NULL,
	data            JSONB,
	start_date      TIMESTAMP WITH TIME ZONE,
	end_date        TIMESTAMP WITH TIME ZONE,
	run_id          BIGINT      NOT NULL REFERENCES pipeline.pipeline_run(run_id),
	duration        BIGINT,
	CONSTRAINT engine_run_task_constrain UNIQUE(task_id, engine_type)
);
