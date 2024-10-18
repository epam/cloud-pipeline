CREATE TABLE IF NOT EXISTS pipeline.archive_run (LIKE pipeline.pipeline_run INCLUDING ALL);
CREATE SEQUENCE pipeline.s_archive_run START WITH 1 INCREMENT BY 1;
ALTER TABLE pipeline.archive_run DROP CONSTRAINT archive_run_pkey;
CREATE INDEX archive_run_end_date_idx ON pipeline.archive_run (end_date);
ALTER TABLE pipeline.archive_run ADD COLUMN ID BIGINT NOT NULL PRIMARY KEY DEFAULT NEXTVAL('pipeline.s_archive_run');

CREATE TABLE IF NOT EXISTS pipeline.archive_run_status_change (LIKE pipeline.run_status_change INCLUDING ALL);
CREATE SEQUENCE pipeline.s_archive_run_status_change START WITH 1 INCREMENT BY 1;
ALTER TABLE pipeline.archive_run_status_change ADD COLUMN ID BIGINT NOT NULL PRIMARY KEY DEFAULT NEXTVAL('pipeline.s_archive_run_status_change');
CREATE INDEX archive_run_status_change_run_id_idx ON pipeline.archive_run_status_change (run_id);
