ALTER TABLE pipeline.datastorage ADD COLUMN path_permissions_enabled BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS pipeline.datastorage_path_permissions (
  folder_path   TEXT    NOT NULL,
  storage_id    BIGINT  REFERENCES pipeline.datastorage(datastorage_id) NOT NULL,
  sid_name      TEXT    NOT NULL,
  principal     BOOLEAN NOT NULL,
  mask          INTEGER NOT NULL,
  file_name     TEXT,
  CONSTRAINT    datastorage_path_permissions_unique UNIQUE(folder_path, storage_id, sid_name, principal, file_name)
);
