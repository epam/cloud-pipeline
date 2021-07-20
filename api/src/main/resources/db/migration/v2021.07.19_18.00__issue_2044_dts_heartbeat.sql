ALTER TABLE pipeline.dts_registry ADD heartbeat TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE pipeline.dts_registry ADD status BIGINT NOT NULL DEFAULT 0;
DELETE FROM pipeline.dts_registry r1
    USING (
        SELECT MIN(id) as id, name
        FROM pipeline.dts_registry
        GROUP BY name HAVING COUNT(*) > 1
    ) r2
    WHERE r1.name = r2.name
    AND r1.id <> r2.id;
ALTER TABLE pipeline.dts_registry ADD CONSTRAINT dts_registry_name_unique UNIQUE (name);
