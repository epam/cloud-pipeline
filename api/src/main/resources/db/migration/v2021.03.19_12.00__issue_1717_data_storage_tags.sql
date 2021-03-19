CREATE TABLE IF NOT EXISTS pipeline.datastorage_root (
    datastorage_root_id      SERIAL                   NOT NULL,
    datastorage_root_path    TEXT                     NOT NULL,
    PRIMARY KEY (datastorage_root_id),
    UNIQUE (datastorage_root_path)
);

CREATE OR REPLACE FUNCTION get_nfs_datastorage_root(path text)
RETURNS text
LANGUAGE plpgsql
AS
$$
declare
    datastorage_root_lustre text;
    datastorage_root_nfs text;
    datastorage_root_nfs_with_home_dir text;
    datastorage_root_azure text;
begin
	SELECT regexp_replace(path, '/$', '')
	INTO path;
	
    SELECT (regexp_matches(path, '(^([^:]+(:\d+)?@\w+)(:([^:]+(:\d+)?@\w+))*(:\/)[^\/]+)'))[1]
    INTO datastorage_root_lustre;
	
	SELECT (regexp_matches(path, '(.+:.*\/)[^\/]*'))[1]
	INTO datastorage_root_nfs;
	
	SELECT (regexp_matches(path, '(.+:)[^\/]+'))[1]
	INTO datastorage_root_nfs_with_home_dir;
	
	SELECT (regexp_matches(path, '([^\/]+\/[^\/]+\/)[^\/]+'))[1]
	INTO datastorage_root_azure;

    return COALESCE(datastorage_root_lustre,
                    datastorage_root_nfs,
                    datastorage_root_nfs_with_home_dir,
                    datastorage_root_azure);
end;
$$;

INSERT INTO pipeline.datastorage_root(datastorage_root_path)
SELECT DISTINCT regexp_replace(path, '/.*$', '')
FROM pipeline.datastorage
WHERE datastorage_type in ('S3', 'AZ', 'GS');

INSERT INTO pipeline.datastorage_root(datastorage_root_path)
SELECT DISTINCT get_nfs_datastorage_root(path)
FROM pipeline.datastorage
WHERE datastorage_type = 'NFS';

ALTER TABLE pipeline.datastorage
ADD COLUMN IF NOT EXISTS datastorage_root_id BIGINT;

ALTER TABLE pipeline.datastorage
ADD CONSTRAINT datastorage_datastorage_root_id_fk
FOREIGN KEY (datastorage_root_id)
REFERENCES datastorage_root (datastorage_root_id);

UPDATE pipeline.datastorage
SET datastorage_root_id = (
   SELECT datastorage_root_id
   FROM pipeline.datastorage_root
   WHERE pipeline.datastorage_root.datastorage_root_path = regexp_replace(pipeline.datastorage.path, '/.*$', '')
)
WHERE datastorage_type in ('S3', 'AZ', 'GS');

UPDATE pipeline.datastorage
SET datastorage_root_id = (
   SELECT datastorage_root_id
   FROM pipeline.datastorage_root
   WHERE pipeline.datastorage_root.datastorage_root_path = get_nfs_datastorage_root(path)
)
WHERE datastorage_type = 'NFS';

ALTER TABLE pipeline.datastorage ALTER COLUMN datastorage_root_id SET NOT NULL;

CREATE TABLE IF NOT EXISTS pipeline.datastorage_tag (
    datastorage_root_id      BIGINT                   NOT NULL,
    datastorage_path         TEXT                     NOT NULL,
    datastorage_version      TEXT                     NOT NULL,
    tag_key                  TEXT                     NOT NULL,
    tag_value                TEXT                     NOT NULL,
    created_date             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (datastorage_root_id, datastorage_path, datastorage_version, tag_key),
    CONSTRAINT datastorage_tag_datastorage_root_id
        FOREIGN KEY (datastorage_root_id)
        REFERENCES pipeline.datastorage_root (datastorage_root_id)
);

CREATE INDEX datastorage_tag_path ON pipeline.datastorage_tag (datastorage_root_id, datastorage_path);
