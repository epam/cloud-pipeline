CREATE TABLE ontology (
    id SERIAL PRIMARY KEY,
    name varchar NOT NULL,
    attributes jsonb,
    parent_id integer REFERENCES ontology(id),
    external_id varchar,
    created timestamp WITHOUT TIME ZONE NOT NULL,
    modified timestamp WITHOUT TIME ZONE NOT NULL,
    type varchar NOT NULL
);
