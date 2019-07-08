DO $$
DECLARE
    user_count BIGINT;
    user_name TEXT;
BEGIN
    SELECT count(*) from pipeline.user into user_count;
    SELECT name from pipeline.user into user_name;

    ALTER TABLE pipeline.user_roles DROP CONSTRAINT user_roles_user_id_fk;
    update pipeline.user set id = ${default.admin.id} where id = 1 and user_count = 1 and user_name = '${default.admin}';
    update pipeline.user_roles set user_id = ${default.admin.id} where user_id = 1 and user_count = 1 and user_name = '${default.admin}';
    ALTER TABLE pipeline.user_roles ADD CONSTRAINT user_roles_user_id_fk FOREIGN KEY (user_id) REFERENCES pipeline.user (id);
    SELECT setval('pipeline.S_USER', ${default.admin.id}, true) where user_count = 1 and user_name = '${default.admin}';
END $$;
