ALTER TABLE pipeline.capacity_reservation
  ADD COLUMN IF NOT EXISTS fallback_candidates JSONB NOT NULL DEFAULT '[]';
