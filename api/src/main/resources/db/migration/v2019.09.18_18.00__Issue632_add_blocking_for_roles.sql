ALTER TABLE pipeline.group_status ADD is_external BOOLEAN NOT NULL;
ALTER TABLE pipeline.group_status DROP CONSTRAINT group_status_pkey;
ALTER TABLE pipeline.group_status ADD PRIMARY KEY (group_name, is_external);
