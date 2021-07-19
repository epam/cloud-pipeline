ALTER TABLE pipeline.dts_registry ADD heartbeat TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE pipeline.dts_registry ADD status BIGINT NOT NULL DEFAULT 0;
