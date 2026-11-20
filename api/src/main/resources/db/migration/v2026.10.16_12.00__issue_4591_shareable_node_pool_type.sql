UPDATE pipeline.node_pool
  SET pool_type = 'SHAREABLE_NODE'
  WHERE pool_type = 'SHARABLE_NODE';
