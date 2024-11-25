import { AclClass } from './acl';
import { PipelineType, RepositoryType } from './enums';

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
