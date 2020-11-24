CREATE SEQUENCE IF NOT EXISTS pipeline.s_node_schedule START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.node_schedule (
  id BIGINT PRIMARY KEY DEFAULT nextval('pipeline.s_node_schedule'),
  created_date TIMESTAMP WITH TIME ZONE NOT NULL,
  name TEXT
);

CREATE TABLE IF NOT EXISTS pipeline.node_schedule_entry (
  schedule_id BIGINT NOT NULL,
  from_day_of_week INT NOT NULL,
  from_time TIME NOT NULL,
  to_day_of_week INT NOT NULL,
  to_time TIME NOT NULL,
  CONSTRAINT schedule_id_entry_fkey FOREIGN KEY (schedule_id) REFERENCES pipeline.node_schedule (id) ON DELETE CASCADE
);

CREATE SEQUENCE IF NOT EXISTS pipeline.s_node_pool START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.node_pool (
  id BIGINT PRIMARY KEY DEFAULT nextval('pipeline.s_node_pool'),
  created_date TIMESTAMP WITH TIME ZONE NOT NULL,
  name TEXT,
  region_id BIGINT NOT NULL,
  instance_type TEXT NOT NULL,
  node_disk INT NOT NULL,
  price_type TEXT NOT NULL,
  docker_image TEXT,
  instance_image TEXT,
  instance_count INT NOT NULL,
  schedule_id BIGINT,
  CONSTRAINT schedule_id_node_fkey FOREIGN KEY (schedule_id) REFERENCES pipeline.node_schedule (id)
);

