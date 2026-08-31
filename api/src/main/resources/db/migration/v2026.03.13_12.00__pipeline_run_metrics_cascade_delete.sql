ALTER TABLE pipeline.pipeline_run_metrics
    DROP CONSTRAINT run_id_fk,
    ADD CONSTRAINT run_id_fk FOREIGN KEY (run_id)
        REFERENCES pipeline.pipeline_run (run_id) ON DELETE CASCADE;
