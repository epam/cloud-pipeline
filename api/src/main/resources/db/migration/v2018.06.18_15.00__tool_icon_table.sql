CREATE SEQUENCE pipeline.s_tool_icon START WITH 1 INCREMENT BY 1;

CREATE TABLE pipeline.tool_icon (
  icon_id BIGINT NOT NULL PRIMARY KEY,
  tool_id BIGINT UNIQUE NOT NULL REFERENCES pipeline.tool(id),
  file_name VARCHAR(256),
  icon bytea NOT NULL
);

ALTER TABLE pipeline.tool ADD COLUMN icon_id BIGINT;
ALTER TABLE pipeline.tool ADD CONSTRAINT tool_icon_id_fk FOREIGN KEY(icon_id) REFERENCES pipeline.tool_icon (icon_id) ON DELETE SET NULL;