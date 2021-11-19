ALTER TABLE pipeline.dts_registry ADD preferences JSONB NOT NULL DEFAULT '{}';
INSERT INTO pipeline.role (id, name, predefined, user_default) VALUES (nextval('pipeline.s_role'), 'ROLE_DTS_MANAGER', TRUE, FALSE);
