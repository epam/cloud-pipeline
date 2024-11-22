import { AclClass } from './acl';

enum RepositoryType {
  gitlab = 'GITLAB',
  github = 'GITHUB',
  bitBucket = 'BITBUCKET',
  bitBucketCloud = 'BITBUCKET_CLOUD',
}

enum PipelineType {
  pipeline = 'PIPELINE',
  versionedStorage = 'VERSIONED_STORAGE',
}

export type Pipeline = {
  aclClass: AclClass.pipeline;
  id: number;
  name: string;
  createdDate: string;
  mask: number;
  owner: string;
  locked: boolean;
  description?: string;
  repository: string;
  repositorySsh: string;
  parentFolderId?: number;
  repositoryType: RepositoryType;
  pipelineType: PipelineType;
  hasMetadata: boolean;
  codePath?: string;
  configurationPath?: string;
  docsPath?: string;
  branch?: string;
  visibility?: 'INHERIT' | 'OWNER';
};
