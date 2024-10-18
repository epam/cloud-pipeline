ALTER TABLE pipeline.pipeline_user_allowed_runners ADD COLUMN pipelines_allowed BOOL;
ALTER TABLE pipeline.pipeline_user_allowed_runners ADD COLUMN tools_allowed BOOL;
ALTER TABLE pipeline.pipeline_user_allowed_runners ADD COLUMN pipelines_list TEXT;
ALTER TABLE pipeline.pipeline_user_allowed_runners ADD COLUMN tools_list TEXT;

