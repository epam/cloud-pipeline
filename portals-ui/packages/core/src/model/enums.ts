export enum RunStatuses {
  success = 'SUCCESS',
  failure = 'FAILURE',
  running = 'RUNNING',
  stopped = 'STOPPED',
  pausing = 'PAUSING',
  paused = 'PAUSED',
  resuming = 'RESUMING',
}

export enum CommitStatuses {
  notCommitted = 'NOT_COMMITTED',
  committing = 'COMMITTING',
  failure = 'FAILURE',
  success = 'SUCCESS',
}

export enum CloudProviders {
  aws = 'AWS',
  azure = 'AZURE',
  gcp = 'GCP',
  local = 'LOCAL',
}

export enum RepositoryType {
  gitlab = 'GITLAB',
  github = 'GITHUB',
  bitBucket = 'BITBUCKET',
  bitBucketCloud = 'BITBUCKET_CLOUD',
}

export enum PipelineType {
  pipeline = 'PIPELINE',
  versionedStorage = 'VERSIONED_STORAGE',
}
