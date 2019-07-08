DO $$
DECLARE
    user_count BIGINT;
    user_name TEXT;
BEGIN
    SELECT count(*) from pipeline.user into user_count;
    SELECT name from pipeline.user into user_name;

    ALTER TABLE pipeline.user_roles DROP CONSTRAINT user_roles_user_id_fk;
    UPDATE pipeline.user SET id = ${default.admin.id} WHERE id = 1 AND user_count = 1 AND user_name = '${default.admin}';
    UPDATE pipeline.user_roles SET user_id = ${default.admin.id} WHERE user_id = 1 AND user_count = 1 AND user_name = '${default.admin}';
    ALTER TABLE pipeline.user_roles ADD CONSTRAINT user_roles_user_id_fk FOREIGN KEY (user_id) REFERENCES pipeline.user (id);
    PERFORM setval('pipeline.S_USER', ${default.admin.id}, true) WHERE user_count = 1 AND user_name = '${default.admin}';
END $$;
