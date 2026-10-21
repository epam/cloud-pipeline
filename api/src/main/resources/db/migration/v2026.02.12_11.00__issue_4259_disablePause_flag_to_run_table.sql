ALTER TABLE pipeline.pipeline_run ADD pause_disabled TEXT NOT NULL DEFAULT FALSE;
ALTER TABLE pipeline.archive_run ADD pause_disabled TEXT NOT NULL DEFAULT FALSE;
