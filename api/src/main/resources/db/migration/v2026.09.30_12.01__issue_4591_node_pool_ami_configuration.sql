-- How a pool's nodes are launched, over the region's image rules: JSON of an AMIConfiguration, NULL for a pool whose
-- nodes launch exactly as the region says.
ALTER TABLE pipeline.node_pool
  ADD COLUMN IF NOT EXISTS ami_configuration TEXT;
