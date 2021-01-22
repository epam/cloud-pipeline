CREATE TABLE pipeline.cloud_profile_credentials_role (
    PRIMARY KEY (cloud_profile_credentials_id, role_id),
    cloud_profile_credentials_id integer NOT NULL REFERENCES pipeline.cloud_profile_credentials(id),
    role_id integer NOT NULL REFERENCES pipeline.role(id)
);
