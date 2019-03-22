CREATE TABLE pipeline.metadata(
    entity_id INTEGER NOT NULL,
    entity_class TEXT NOT NULL,
    data JSONB NOT NULL,
    PRIMARY KEY(entity_id, entity_class)
);
CREATE INDEX metadata_index_jsonb ON pipeline.metadata USING GIN (data);
CREATE INDEX metadata_index ON pipeline.metadata (entity_id, entity_class);
