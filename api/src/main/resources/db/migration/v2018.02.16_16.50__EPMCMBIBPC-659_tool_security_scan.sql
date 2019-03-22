ALTER TABLE pipeline.docker_registry ADD COLUMN security_scan_enabled BOOLEAN DEFAULT false;
CREATE SEQUENCE pipeline.s_tool_vulnerability START WITH 1 INCREMENT BY 1;

CREATE TABLE pipeline.tool_vulnerability (
  vulnerability_id BIGINT NOT NULL PRIMARY KEY DEFAULT NEXTVAL('pipeline.s_tool_vulnerability'),
  tool_id BIGINT NOT NULL REFERENCES pipeline.tool (id),
  version VARCHAR(256) NOT NULL,
  vulnerability_name VARCHAR(256) NOT NULL,
  feature VARCHAR(1024),
  description TEXT,
  link TEXT,
  severity INTEGER NOT NULL,
  fixed_by TEXT,
  created_date TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX tool_vulnerability_tool_id_idx ON pipeline.tool_vulnerability (tool_id);

-- contains status code: PENDING/COMPLETED/FAILED
ALTER TABLE pipeline.tool ADD COLUMN scan_status INTEGER;
-- contains the date and time of completion/failure
ALTER TABLE pipeline.tool ADD COLUMN scan_date TIMESTAMP WITH TIME ZONE;
-- defines if tool has ever been scanned successfully
ALTER TABLE pipeline.tool ADD COLUMN scanned BOOLEAN DEFAULT false;