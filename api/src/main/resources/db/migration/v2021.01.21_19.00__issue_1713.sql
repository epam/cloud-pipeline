CREATE TABLE cloud_profile_credentials (
    id SERIAL PRIMARY KEY,
    cloud_provider varchar,
    type varchar,
    policy varchar,
    profile_name varchar,
    assumed_role varchar
);
