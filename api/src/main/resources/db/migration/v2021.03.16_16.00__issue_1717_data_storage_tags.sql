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

CREATE OR REPLACE FUNCTION create_datastorage_root_from_datastorage_function()
RETURNS TRIGGER AS
$BODY$
BEGIN
    IF new.datastorage_type in ('S3', 'AZ', 'GS') THEN
        INSERT INTO pipeline.datastorage_root(datastorage_root_path)
        VALUES (regexp_replace(new.path, '/.*$', ''))
        ON CONFLICT (datastorage_root_path)
        DO NOTHING;
        RETURN new;
    ELSEIF new.datastorage_type = 'NFS' THEN
        INSERT INTO pipeline.datastorage_root(datastorage_root_path)
        VALUES (get_nfs_datastorage_root(path))
        ON CONFLICT (datastorage_root_path)
        DO NOTHING;
        RETURN new;
    END IF;
END;
$BODY$
language plpgsql;

CREATE TRIGGER create_datastorage_root_trigger
     AFTER INSERT ON pipeline.datastorage
     FOR EACH ROW
     EXECUTE PROCEDURE create_datastorage_root_from_datastorage_function();

INSERT INTO pipeline.datastorage_root(datastorage_root_path)
SELECT DISTINCT regexp_replace(path, '/.*$', '')
FROM pipeline.datastorage
WHERE datastorage_type in ('S3', 'AZ', 'GS');

INSERT INTO pipeline.datastorage_root(datastorage_root_path)
SELECT DISTINCT get_nfs_datastorage_root(path)
FROM pipeline.datastorage
WHERE datastorage_type = 'NFS';

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
