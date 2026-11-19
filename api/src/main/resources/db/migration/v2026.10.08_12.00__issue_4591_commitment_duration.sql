ALTER TABLE pipeline.capacity_reservation
  RENAME COLUMN duration_hours TO commitment_duration;

ALTER TABLE pipeline.capacity_reservation
  ALTER COLUMN commitment_duration TYPE BIGINT;

UPDATE pipeline.capacity_reservation
  SET commitment_duration = commitment_duration * 3600
  WHERE commitment_duration IS NOT NULL;

ALTER TABLE pipeline.capacity_reservation
  RENAME COLUMN granted_commitment_seconds TO granted_commitment_duration;
