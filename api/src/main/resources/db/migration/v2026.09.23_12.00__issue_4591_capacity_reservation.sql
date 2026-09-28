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

-- A node pool has at most one capacity reservation, and a reservation never exists without its pool.
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

-- Pools that predate the owner column have nobody to attribute them to, and the permission layer
-- dereferences the owner without a null check, so leaving them NULL would fail an ACL check on any
-- pre-existing pool. Attribute them to the default administrator instead.
UPDATE pipeline.node_pool SET owner = '${default.admin}' WHERE owner IS NULL;

INSERT INTO pipeline.role (id, name, predefined, user_default)
VALUES (nextval('pipeline.s_role'), 'ROLE_NODE_POOL_MANAGER', TRUE, FALSE);

INSERT INTO pipeline.acl_class (class)
VALUES ('com.epam.pipeline.entity.cluster.pool.NodePool');
