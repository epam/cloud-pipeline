ALTER TABLE pipeline.node_pool
ADD COLUMN autoscaled           BOOLEAN             NOT NULL DEFAULT FALSE,
ADD COLUMN min_size             INT                 NULL,
ADD COLUMN max_size             INT                 NULL,
ADD COLUMN scale_up_threshold   DOUBLE PRECISION    NULL,
ADD COLUMN scale_down_threshold DOUBLE PRECISION    NULL,
ADD COLUMN scale_step           INT                 NULL;
