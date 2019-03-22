ALTER TABLE PIPELINE.PIPELINE_RUN ADD PARENT_ID BIGINT NULL;

UPDATE PIPELINE.PIPELINE_RUN SET
  PARENT_ID = subquery.parent
FROM (
       SELECT DISTINCT
         RUN_ID as id,
         split_part(unnest(regexp_matches(parameters, 'parent-id=[\d]+')), '=', 2)::BIGINT as parent
       FROM pipeline.pipeline_run
       WHERE parameters like any(array['%%|parent-id=%%','%%|parent-id=%%', 'parent-id=%%'])
     ) AS subquery

WHERE
  RUN_ID = subquery.id;

