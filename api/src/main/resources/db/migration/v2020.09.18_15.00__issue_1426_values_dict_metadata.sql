CREATE TABLE IF NOT EXISTS pipeline.categorical_attributes(
  key TEXT,
  value TEXT,
  UNIQUE(key, value)
);
