-- A Table to store last layer reference for each tool version
CREATE TABLE pipeline.tool_layers (
  tool_id BIGINT NOT NULL REFERENCES pipeline.tool (id),
  version VARCHAR(256) NOT NULL,
  layer_reference VARCHAR(128) PRIMARY KEY
);

ALTER TABLE pipeline.tool_vulnerability ADD COLUMN feature_version VARCHAR(1024);