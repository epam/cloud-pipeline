ALTER TABLE pipeline.pipeline_run ADD COLUMN original_owner TEXT NOT NULL DEFAULT '';
UPDATE pipeline.pipeline_run SET original_owner = owner WHERE original_owner = '';

DO $$
DECLARE
  original_owner_env TEXT;
  current_run_id BIGINT;
BEGIN
  FOR current_run_id, original_owner_env in SELECT run_id, env_vars::json->>'ORIGINAL_OWNER' FROM pipeline.pipeline_run
  loop
    IF original_owner_env != '' THEN
      UPDATE pipeline.pipeline_run SET original_owner = original_owner_env WHERE run_id = current_run_id;
    END IF;
  END LOOP;
END $$;


ALTER TABLE pipeline.archive_run ADD COLUMN original_owner TEXT NOT NULL DEFAULT '';
UPDATE pipeline.archive_run SET original_owner = owner WHERE original_owner = '';

DO $$
DECLARE
  original_owner_env TEXT;
  current_run_id BIGINT;
BEGIN
  FOR current_run_id, original_owner_env in SELECT run_id, env_vars::json->>'ORIGINAL_OWNER' FROM pipeline.archive_run
  loop
    IF original_owner_env != '' THEN
      UPDATE pipeline.archive_run SET original_owner = original_owner_env WHERE run_id = current_run_id;
    END IF;
  END LOOP;
END $$;
