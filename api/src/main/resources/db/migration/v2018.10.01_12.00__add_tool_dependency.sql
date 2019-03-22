CREATE SEQUENCE pipeline.s_tool_dependency START WITH 1 INCREMENT BY 1;

CREATE TABLE pipeline.tool_dependency (
  dependency_id BIGINT NOT NULL PRIMARY KEY DEFAULT NEXTVAL('pipeline.s_tool_dependency'),
  tool_id BIGINT NOT NULL REFERENCES pipeline.tool (id),
  tool_version VARCHAR(256) NOT NULL,
  dependency_name VARCHAR(256) NOT NULL,
  dependency_ecosystem VARCHAR(256) NOT NULL,
  dependency_version VARCHAR(256)
);