CREATE SEQUENCE IF NOT EXISTS pipeline.S_CATEGORICAL_ATTRIBUTES START WITH 1 INCREMENT BY 1;
ALTER TABLE pipeline.categorical_attributes ADD id BIGINT PRIMARY KEY DEFAULT nextval('pipeline.S_CATEGORICAL_ATTRIBUTES');

CREATE TABLE IF NOT EXISTS pipeline.categorical_attributes_links(
  parent_id BIGINT NOT NULL REFERENCES pipeline.categorical_attributes (id) ON DELETE CASCADE ON UPDATE CASCADE,
  child_id BIGINT NOT NULL REFERENCES pipeline.categorical_attributes (id) ON DELETE CASCADE ON UPDATE CASCADE,
  autofill BOOLEAN DEFAULT FALSE,
  UNIQUE(parent_id, child_id)
);
