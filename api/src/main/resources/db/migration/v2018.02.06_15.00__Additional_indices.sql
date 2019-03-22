CREATE INDEX IF NOT EXISTS metadata_entity_parent_id_index ON pipeline.metadata_entity (parent_id);
CREATE INDEX IF NOT EXISTS folder_parent_id_index ON pipeline.folder (parent_id);
