CREATE TABLE pipeline.attachment (
  attachment_id BIGINT NOT NULL PRIMARY KEY,
  attachment_name VARCHAR(1024) NOT NULL,
  issue_id BIGINT REFERENCES pipeline.issue (issue_id),
  comment_id BIGINT REFERENCES pipeline.issue_comment (comment_id),
  path TEXT NOT NULL,
  created_date TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE SEQUENCE pipeline.s_attachment START WITH 1 INCREMENT BY 1;