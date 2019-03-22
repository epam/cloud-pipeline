INSERT INTO pipeline.datastorage(
        datastorage_id, datastorage_name, description, datastorage_type, path, created_date, sts_duration, lts_duration)
        VALUES (1, 'OUTPUT', 'Machine run data', 'S3', :'s3_out', now(), NULL, NULL);

INSERT INTO pipeline.datastorage(
        datastorage_id, datastorage_name, description, datastorage_type, path, created_date, sts_duration, lts_duration)
        VALUES (2, 'ANALYSIS', 'Pipeline analysis data', 'S3', :'s3_analis', now(), NULL, NULL);

INSERT INTO pipeline.datastorage(
        datastorage_id, datastorage_name, description, datastorage_type, path, created_date, sts_duration, lts_duration)
        VALUES (3, 'REFERENCE', 'Reference data', 'S3', :'s3_ref', now(), NULL, NULL);

INSERT INTO pipeline.datastorage(
        datastorage_id, datastorage_name, description, datastorage_type, path, created_date, sts_duration, lts_duration)
        VALUES (4, 'LIMS', 'LIMS data', 'S3', :'s3_lims', now(), NULL, NULL);

ALTER SEQUENCE pipeline.s_datastorage RESTART WITH 5;

