CREATE TABLE pipeline.aws_region (
  aws_region_id  BIGSERIAL NOT NULL PRIMARY KEY,
  region_id      TEXT NOT NULL UNIQUE,
  name           TEXT NOT NULL UNIQUE,
  is_default     BOOLEAN,
  cors_rules     TEXT,
  policy         TEXT,
  kms_key_id     TEXT,
  kms_key_arn    TEXT,
  owner          TEXT,
  created_date   TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE pipeline.datastorage ADD region_id bigint NULL;
ALTER TABLE pipeline.datastorage ADD CONSTRAINT datastorage_aws_region_aws_region_id_fk FOREIGN KEY (region_id) REFERENCES aws_region (aws_region_id);