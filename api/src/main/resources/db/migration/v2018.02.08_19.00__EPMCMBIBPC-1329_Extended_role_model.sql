ALTER TABLE pipeline.role ADD predefined BOOLEAN DEFAULT FALSE  NOT NULL;
ALTER TABLE pipeline.role ADD user_default BOOLEAN DEFAULT FALSE  NOT NULL;

INSERT INTO pipeline.role (id, name, predefined, user_default) VALUES (nextval('pipeline.s_role'), 'ROLE_PIPELINE_MANAGER', TRUE, TRUE);
INSERT INTO pipeline.role (id, name, predefined, user_default) VALUES (nextval('pipeline.s_role'), 'ROLE_FOLDER_MANAGER', TRUE, TRUE);
INSERT INTO pipeline.role (id, name, predefined, user_default) VALUES (nextval('pipeline.s_role'), 'ROLE_CONFIGURATION_MANAGER', TRUE, TRUE);
INSERT INTO pipeline.role (id, name, predefined, user_default) VALUES (nextval('pipeline.s_role'), 'ROLE_STORAGE_MANAGER', TRUE, FALSE);
INSERT INTO pipeline.role (id, name, predefined, user_default) VALUES (nextval('pipeline.s_role'), 'ROLE_TOOL_GROUP_MANAGER', TRUE, FALSE);
INSERT INTO pipeline.role (id, name, predefined, user_default) VALUES (nextval('pipeline.s_role'), 'ROLE_ENTITIES_MANAGER', TRUE, FALSE);

UPDATE pipeline.role SET predefined = TRUE WHERE role.id IN (1, 2);
UPDATE pipeline.role SET user_default = TRUE WHERE role.id = 2;

INSERT INTO pipeline.user_roles (user_id, role_id)
  SELECT
    u.id as user_id,
    r2.id as role_id
  FROM pipeline."user" u
    INNER JOIN pipeline.user_roles r ON u.id = r.user_id
    CROSS JOIN pipeline.role r2
  WHERE r.role_id = 2 AND r2.name IN ('ROLE_PIPELINE_MANAGER', 'ROLE_FOLDER_MANAGER', 'ROLE_CONFIGURATION_MANAGER')
;
