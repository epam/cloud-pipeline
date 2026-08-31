INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_TOOL_ADMIN', TRUE, FALSE);

INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_USER_ADMIN', TRUE, FALSE);

INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_PIPELINE_ADMIN', TRUE, FALSE);

INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_RUN_ADMIN', TRUE, FALSE);
