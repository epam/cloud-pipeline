CREATE SEQUENCE pipeline.s_tool_group START WITH 1 INCREMENT BY 1;

CREATE TABLE pipeline.tool_group (
  id BIGINT NOT NULL PRIMARY KEY,
  group_name TEXT NOT NULL,
  registry_id BIGINT NOT NULL REFERENCES pipeline.docker_registry(id),
  owner TEXT NOT NULL,
  UNIQUE (group_name, registry_id)
);

ALTER TABLE pipeline.tool ADD COLUMN tool_group_id BIGINT;
ALTER TABLE pipeline.tool ADD CONSTRAINT tool_group_id_fk FOREIGN KEY (tool_group_id) REFERENCES pipeline.tool_group(id);
ALTER TABLE pipeline.tool ADD CONSTRAINT tool_image_unique UNIQUE (image, tool_group_id);

-- Add library groups to all registries
INSERT INTO pipeline.tool_group (id, group_name, registry_id, owner)
SELECT NEXTVAL('pipeline.s_tool_group'), 'library', r.id, 'Unauthorized'
FROM pipeline.docker_registry r;

-- update image names
UPDATE pipeline.tool AS tool
SET
  tool_group_id = g.id,
  image = 'library/' || image
FROM
  pipeline.tool_group g
WHERE
	g.group_name = 'library' AND tool.registry_id = g.registry_id;

ALTER TABLE pipeline.tool ALTER COLUMN tool_group_id SET NOT NULL;