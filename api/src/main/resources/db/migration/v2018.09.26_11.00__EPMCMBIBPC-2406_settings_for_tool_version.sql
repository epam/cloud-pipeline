ALTER TABLE pipeline.tool_version ADD COLUMN settings JSONB;
ALTER TABLE pipeline.tool_version ALTER COLUMN size DROP NOT NULL;
ALTER TABLE pipeline.tool_version ALTER COLUMN digest DROP NOT NULL;
ALTER TABLE pipeline.tool_version ALTER COLUMN modified_date DROP NOT NULL;