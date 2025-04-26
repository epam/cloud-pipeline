CREATE SEQUENCE IF NOT EXISTS pipeline.s_ui_plugin START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS pipeline.s_ui_plugin_assignment START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.ui_plugin(
    id bigint NOT NULL DEFAULT nextval('pipeline.s_ui_plugin'),
    name character varying(2048) NOT NULL,
    type character varying(256) NOT NULL,
    path character varying(2048) NOT NULL,
    CONSTRAINT ui_plugin_pkey PRIMARY KEY (id),
    CONSTRAINT unique_plugin_path UNIQUE (path)
);

CREATE TABLE IF NOT EXISTS pipeline.ui_plugin_assignment(
    id bigint NOT NULL DEFAULT nextval('pipeline.s_ui_plugin_assignment'),
    plugin_id bigint NOT NULL,
    tool_id bigint,
    pipeline_id bigint,
    version character varying(256),
    CONSTRAINT ui_plugin_assignment_pkey PRIMARY KEY (id),
    CONSTRAINT check_tool_or_pipeline CHECK (
        (tool_id IS NOT NULL AND pipeline_id IS NULL) OR
        (tool_id IS NULL AND pipeline_id IS NOT NULL)
    ),
    CONSTRAINT unique_plugin_assignment_pipeline UNIQUE (plugin_id, pipeline_id, version),
    CONSTRAINT unique_plugin_assignment_tool UNIQUE (plugin_id, tool_id, version),
    CONSTRAINT ui_plugin_assignment_pipeline_id_fkey FOREIGN KEY (pipeline_id) REFERENCES pipeline.pipeline (pipeline_id),
    CONSTRAINT ui_plugin_assignment_plugin_id_fkey FOREIGN KEY (plugin_id) REFERENCES pipeline.ui_plugin (id),
    CONSTRAINT ui_plugin_assignment_tool_id_fkey FOREIGN KEY (tool_id) REFERENCES pipeline.tool (id)
);

CREATE TABLE IF NOT EXISTS pipeline.ui_plugin_assignment_sids
(
    assignment_id bigint NOT NULL,
    name character varying(512) NOT NULL,
    principal boolean NOT NULL,
    CONSTRAINT ui_plugin_assignment_sids_assignment_id_fkey FOREIGN KEY (assignment_id)
    REFERENCES pipeline.ui_plugin_assignment (id)
);