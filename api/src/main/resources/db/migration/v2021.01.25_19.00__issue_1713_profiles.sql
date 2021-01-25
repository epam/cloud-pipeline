CREATE TABLE cloud_profile_credentials (
    id SERIAL PRIMARY KEY,
    cloud_provider varchar,
    type varchar,
    policy varchar,
    profile_name varchar,
    assumed_role varchar
);
CREATE TABLE pipeline.cloud_profile_credentials_user (
    PRIMARY KEY (cloud_profile_credentials_id, user_id),
    cloud_profile_credentials_id integer NOT NULL REFERENCES pipeline.cloud_profile_credentials(id),
    user_id integer NOT NULL REFERENCES pipeline.user(id)
);
CREATE TABLE pipeline.cloud_profile_credentials_role (
    PRIMARY KEY (cloud_profile_credentials_id, role_id),
    cloud_profile_credentials_id integer NOT NULL REFERENCES pipeline.cloud_profile_credentials(id),
    role_id integer NOT NULL REFERENCES pipeline.role(id)
);
ALTER TABLE pipeline.user ADD COLUMN default_profile_id integer REFERENCES pipeline.cloud_profile_credentials(id) NULL;
ALTER TABLE pipeline.role ADD COLUMN default_profile_id integer REFERENCES pipeline.cloud_profile_credentials(id) NULL;
