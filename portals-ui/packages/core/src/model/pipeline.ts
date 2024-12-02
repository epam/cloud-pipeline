import { AclClass, AclEntry } from './acl';
import { PipelineType, RepositoryType } from './enums';

export type Pipeline = AclEntry<AclClass.pipeline> & {
  id: number;
  name: string;
  createdDate: string;
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
  data?: Record<string, any>;
};
