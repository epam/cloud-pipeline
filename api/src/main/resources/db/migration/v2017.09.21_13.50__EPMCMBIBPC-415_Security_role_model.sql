CREATE TABLE pipeline.user(
    id BIGINT NOT NULL primary key,
    name text NOT NULL,
    CONSTRAINT unique_key_user_name UNIQUE (name)
);

CREATE TABLE pipeline.role(
    id BIGINT NOT NULL primary key,
    name text NOT NULL,
    CONSTRAINT unique_key_role_name UNIQUE (name)
);

CREATE TABLE pipeline.user_roles
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT unique_key_user_roles UNIQUE (user_id,role_id),
    CONSTRAINT user_roles_user_id_fk FOREIGN KEY (user_id) REFERENCES pipeline.user (id),
    CONSTRAINT user_roles_role_id_fk FOREIGN KEY (role_id) REFERENCES pipeline.role (id)
);

INSERT INTO pipeline.user (id, name) VALUES (1, '${default.admin}');
INSERT INTO pipeline.role (id, name) VALUES (1, 'ROLE_ADMIN');
INSERT INTO pipeline.role (id, name) VALUES (2, 'ROLE_USER');
INSERT INTO pipeline.user_roles (user_id, role_id) VALUES (1, 1);

CREATE SEQUENCE pipeline.S_USER START WITH 2 INCREMENT BY 1;
CREATE SEQUENCE pipeline.S_ROLE START WITH 3 INCREMENT BY 1;

CREATE TABLE pipeline.acl_sid(
    id bigserial not null primary key,
    principal boolean not null,
    sid text not null,
    constraint unique_uk_1 unique(sid,principal)
);

CREATE TABLE pipeline.acl_class(
    id bigserial not null primary key,
    class varchar(100) not null,
    constraint unique_uk_2 unique(class)
);

INSERT INTO pipeline.acl_class (class) VALUES ('com.epam.pipeline.entity.datastorage.AbstractDataStorage');
INSERT INTO pipeline.acl_class (class) VALUES ('com.epam.pipeline.entity.pipeline.DockerRegistry');
INSERT INTO pipeline.acl_class (class) VALUES ('com.epam.pipeline.entity.pipeline.Pipeline');
INSERT INTO pipeline.acl_class (class) VALUES ('com.epam.pipeline.entity.pipeline.Folder');
INSERT INTO pipeline.acl_class (class) VALUES ('com.epam.pipeline.entity.pipeline.Tool');

CREATE TABLE pipeline.acl_object_identity(
    id bigserial primary key,
    object_id_class bigint not null,
    object_id_identity bigint not null,
    parent_object bigint,
    owner_sid bigint,
    entries_inheriting boolean not null,
    constraint unique_uk_3 unique(object_id_class,object_id_identity),
    constraint foreign_fk_1 foreign key(parent_object)references acl_object_identity(id),
    constraint foreign_fk_2 foreign key(object_id_class)references acl_class(id),
    constraint foreign_fk_3 foreign key(owner_sid)references acl_sid(id)
);

CREATE TABLE pipeline.acl_entry(
    id bigserial primary key,
    acl_object_identity bigint not null,
    ace_order int not null,
    sid bigint not null,
    mask integer not null,
    granting boolean not null,
    audit_success boolean not null,
    audit_failure boolean not null,
    constraint unique_uk_4 unique(acl_object_identity,ace_order),
    constraint foreign_fk_4 foreign key(acl_object_identity) references acl_object_identity(id),
    constraint foreign_fk_5 foreign key(sid) references acl_sid(id)
);
