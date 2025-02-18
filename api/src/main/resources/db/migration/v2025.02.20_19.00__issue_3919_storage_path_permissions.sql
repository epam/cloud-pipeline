ALTER TABLE pipeline.datastorage ADD COLUMN has_path_permissions BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS pipeline.datastorage_path_permissions (
  path          TEXT    NOT NULL,
  storage_id    BIGINT  REFERENCES pipeline.datastorage(datastorage_id) NOT NULL,
  sid_id        TEXT    NOT NULL,
  principal     BOOLEAN NOT NULL,
  mask          INTEGER NOT NULL,
  file_name     TEXT,
  CONSTRAINT    datastorage_path_permissions_unique UNIQUE(path, storage_id, sid_id, principal, file_name)
);
