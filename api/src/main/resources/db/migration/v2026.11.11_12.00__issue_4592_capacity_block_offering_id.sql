ALTER TABLE pipeline.capacity_reservation
  ADD COLUMN IF NOT EXISTS capacity_block_offering_id TEXT;
