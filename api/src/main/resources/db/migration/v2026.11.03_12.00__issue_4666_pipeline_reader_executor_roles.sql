INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_PIPELINE_READER', TRUE, FALSE);
INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_PIPELINE_EXECUTOR', TRUE, FALSE);
