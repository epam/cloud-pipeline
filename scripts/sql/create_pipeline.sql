INSERT INTO pipeline.pipeline(
	pipeline_id, pipeline_name, description, repository, created_date)
	VALUES (1, 'Demultiplex', 
            'BCL2FASTQ demultiplexing pipeline, used to convert raw sequencer data (BCL) files into FASTQ and run subsequent analytical pipelines according to samplesheet instructions', 
            :'dem_rep', now());
INSERT INTO pipeline.datastorage_rule(pipeline_id, file_mask, move_to_sts, created_date)
VALUES (1, '*', true, now());

INSERT INTO pipeline.pipeline(
	pipeline_id, pipeline_name, description, repository, created_date)
	VALUES (6, 'Capture', 'Capture Pipeline implementation', :'cap_rep', now());
INSERT INTO pipeline.datastorage_rule(pipeline_id, file_mask, move_to_sts, created_date)
VALUES (6, '*', true, now());  

INSERT INTO pipeline.pipeline(
	pipeline_id, pipeline_name, description, repository, created_date)
	VALUES (7, 'Amplicon', 'Amplicon pipeline implementation', :'amp_rep', now());
INSERT INTO pipeline.datastorage_rule(pipeline_id, file_mask, move_to_sts, created_date)
VALUES (7, '*', true, now());  

INSERT INTO pipeline.pipeline(
	pipeline_id, pipeline_name, description, repository, created_date)
	VALUES (8, 'Batch', 'Batch pipeline is a set of steps that perform samples batch processing orchestration', :'bach_rep', now());
INSERT INTO pipeline.datastorage_rule(pipeline_id, file_mask, move_to_sts, created_date)
VALUES (8, '*', true, now());  

ALTER SEQUENCE pipeline.s_pipeline RESTART WITH 9;

