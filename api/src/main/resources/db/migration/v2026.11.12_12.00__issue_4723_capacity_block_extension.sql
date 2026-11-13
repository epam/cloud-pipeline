CREATE SEQUENCE IF NOT EXISTS pipeline.s_capacity_block_extension START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS pipeline.capacity_block_extension (
  id BIGINT PRIMARY KEY DEFAULT nextval('pipeline.s_capacity_block_extension'),
  reservation_id BIGINT NOT NULL,
  origin_id BIGINT,
  extension_offering_id TEXT NOT NULL,
  duration_hours INT NOT NULL,
  upfront_fee_amount NUMERIC,
  upfront_fee_currency TEXT,
  status TEXT NOT NULL,
  status_reason TEXT,
  requested_by TEXT,
  created TIMESTAMP WITH TIME ZONE NOT NULL,
  updated TIMESTAMP WITH TIME ZONE NOT NULL,
  start_date TIMESTAMP WITH TIME ZONE,
  end_date TIMESTAMP WITH TIME ZONE,
  CONSTRAINT capacity_block_extension_reservation_fkey FOREIGN KEY (reservation_id)
      REFERENCES pipeline.capacity_reservation (id) ON DELETE CASCADE,
  CONSTRAINT capacity_block_extension_origin_fkey FOREIGN KEY (origin_id)
      REFERENCES pipeline.capacity_block_extension (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS capacity_block_extension_reservation_idx
  ON pipeline.capacity_block_extension (reservation_id);
CREATE INDEX IF NOT EXISTS capacity_block_extension_status_idx
  ON pipeline.capacity_block_extension (status);
