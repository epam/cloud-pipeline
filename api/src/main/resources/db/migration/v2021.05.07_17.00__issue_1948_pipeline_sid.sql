CREATE TABLE pipeline.pipeline_user_allowed_runners (
    pipeline_user_id integer not null,
    principal boolean not null,
    name text not null,
    access_type text not null
);
