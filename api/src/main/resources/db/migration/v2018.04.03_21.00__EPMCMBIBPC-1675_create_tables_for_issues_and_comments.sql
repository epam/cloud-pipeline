CREATE SEQUENCE pipeline.s_issue START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE pipeline.s_issue_comment START WITH 1 INCREMENT BY 1;

CREATE TABLE pipeline.issue (
    issue_id BIGINT NOT NULL PRIMARY KEY,
    issue_name TEXT NOT NULL,
    issue_text TEXT NOT NULL,
    issue_author TEXT NOT NULL,
    entity_id BIGINT NOT NULL,
    entity_class TEXT NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_date TIMESTAMP WITH TIME ZONE NOT NULL,
    issue_status BIGINT DEFAULT 0 NOT NULL,
    labels TEXT[]
);

CREATE TABLE pipeline.issue_comment (
    comment_id BIGINT NOT NULL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    comment_text TEXT NOT NULL,
    comment_author TEXT NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT issue_comment_id_fk FOREIGN KEY (issue_id) REFERENCES pipeline.issue (issue_id)
);
