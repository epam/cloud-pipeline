ALTER TABLE pipeline.categorical_attributes RENAME TO categorical_attributes_values;
ALTER SEQUENCE pipeline.s_categorical_attributes RENAME TO s_categorical_attributes_values;
ALTER TABLE pipeline.categorical_attributes_links RENAME TO categorical_attributes_values_links;

CREATE SEQUENCE IF NOT EXISTS pipeline.s_categorical_attributes START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.categorical_attributes (
  id BIGINT PRIMARY KEY DEFAULT nextval('pipeline.s_categorical_attributes'),
  name TEXT NOT NULL UNIQUE,
  created_date TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  owner TEXT DEFAULT 'Unauthorized' NOT NULL
);

INSERT INTO pipeline.categorical_attributes(name)
    SELECT DISTINCT key FROM pipeline.categorical_attributes_values;

ALTER TABLE pipeline.categorical_attributes_values ADD COLUMN attribute_id BIGINT;

UPDATE pipeline.categorical_attributes_values
    SET attribute_id = categorical_attributes.id
    FROM pipeline.categorical_attributes
    WHERE pipeline.categorical_attributes_values.key = pipeline.categorical_attributes.name;
ALTER TABLE pipeline.categorical_attributes_values DROP COLUMN key;

ALTER TABLE pipeline.categorical_attributes_values ADD CONSTRAINT categorical_attributes_values_attribute_id_fk
    FOREIGN KEY (attribute_id) REFERENCES categorical_attributes(id) ON DELETE CASCADE ON UPDATE CASCADE;