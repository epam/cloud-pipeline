ALTER TABLE pipeline.group_status ADD last_modification_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE pipeline.user ADD block_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE pipeline.user ADD last_login_date TIMESTAMP WITH TIME ZONE;
