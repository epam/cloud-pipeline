CREATE SEQUENCE pipeline.s_tool_version START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.tool_version (
    id BIGINT NOT NULL PRIMARY KEY,
    tool_id BIGINT NOT NULL,
    version TEXT NOT NULL,
    digest TEXT NOT NULL,
    size BIGINT NOT NULL,
    modified_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT tool_id_fkey FOREIGN KEY (tool_id) REFERENCES pipeline.tool (id),
    CONSTRAINT unique_1 UNIQUE(version, tool_id),
    CONSTRAINT unique_2 UNIQUE(digest)
);

CREATE INDEX tool_version_index ON pipeline.tool_version (version, tool_id);