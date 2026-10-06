CREATE SEQUENCE IF NOT EXISTS pipeline.s_capacity_reservation START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.capacity_reservation (
  id BIGINT PRIMARY KEY DEFAULT nextval('pipeline.s_capacity_reservation'),
  node_pool_id BIGINT NOT NULL,
  origin_id BIGINT,
  name TEXT,
  region_id BIGINT NOT NULL,
  cloud_reservation_id TEXT,
  availability_zone TEXT,
  client_token TEXT,
  cloud_provider TEXT NOT NULL,
  reservation_type TEXT NOT NULL,
  status TEXT NOT NULL,
  status_reason TEXT,
  owner TEXT,
  created TIMESTAMP WITH TIME ZONE NOT NULL,
  updated TIMESTAMP WITH TIME ZONE NOT NULL,
  instance_type TEXT NOT NULL,
  instance_count INT NOT NULL,
  requested_start_date TIMESTAMP WITH TIME ZONE,
  requested_end_date TIMESTAMP WITH TIME ZONE,
  start_date TIMESTAMP WITH TIME ZONE,
  end_date TIMESTAMP WITH TIME ZONE,
  duration_hours INT,
  instance_platform TEXT NOT NULL DEFAULT 'Linux/UNIX',
  attempt INT NOT NULL DEFAULT 0,
  granted_commitment_seconds BIGINT,
  CONSTRAINT capacity_reservation_node_pool_fkey FOREIGN KEY (node_pool_id)
      REFERENCES pipeline.node_pool (id),
  CONSTRAINT capacity_reservation_origin_fkey FOREIGN KEY (origin_id)
      REFERENCES pipeline.capacity_reservation (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS capacity_reservation_node_pool_idx
  ON pipeline.capacity_reservation (node_pool_id);
CREATE INDEX IF NOT EXISTS capacity_reservation_status_idx
  ON pipeline.capacity_reservation (status);

ALTER TABLE pipeline.node_pool
  ADD COLUMN IF NOT EXISTS owner TEXT,
  ADD COLUMN IF NOT EXISTS pool_type TEXT NOT NULL DEFAULT 'STANDARD',
  ADD COLUMN IF NOT EXISTS capacity_reservation BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS start_date TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS end_date TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS launch_config TEXT;

UPDATE pipeline.node_pool SET owner = '${default.admin}' WHERE owner IS NULL;

INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_NODE_POOL_MANAGER', TRUE, FALSE);

INSERT INTO pipeline.acl_class (class)
VALUES ('com.epam.pipeline.entity.cluster.pool.NodePool');

ALTER TABLE pipeline.node_pool
  ADD COLUMN IF NOT EXISTS ami_configuration TEXT;

INSERT INTO pipeline.acl_sid (principal, sid)
SELECT DISTINCT TRUE, upper(pool.owner)
FROM pipeline.node_pool pool
WHERE pool.owner IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM pipeline.acl_sid existing WHERE existing.principal AND existing.sid = upper(pool.owner));

INSERT INTO pipeline.acl_object_identity (object_id_class, object_id_identity, owner_sid, entries_inheriting)
SELECT pool_class.id, pool.id, owner_sid.id, TRUE
FROM pipeline.node_pool pool
  JOIN pipeline.acl_class pool_class ON pool_class.class = 'com.epam.pipeline.entity.cluster.pool.NodePool'
  JOIN pipeline.acl_sid owner_sid ON owner_sid.principal AND owner_sid.sid = upper(pool.owner)
WHERE NOT EXISTS (
  SELECT 1 FROM pipeline.acl_object_identity existing
  WHERE existing.object_id_class = pool_class.id AND existing.object_id_identity::text = pool.id::text);
