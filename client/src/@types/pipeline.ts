import type {Pipeline, PipelineType, RepositoryType, RunVisibilityPolicy} from './library.ts';
import type {ConfigurationEntry, ConfigurationParameters} from './library.ts';

export type PipelineVO = {
  id?: number;
  name?: string;
  description?: string;
  repository?: string;
  repositorySsh?: string;
  repositoryToken?: string;
  parentFolderId?: number;
  templateId?: string;
  repositoryType?: RepositoryType;
  pipelineType?: PipelineType;
  branch?: string;
  visibility?: RunVisibilityPolicy;
  configurationPath?: string;
  codePath?: string;
  docsPath?: string;
};

export type Revision = {
  id: number;
  name: string;
  createdDate: string;
  description?: string;
  author?: string;
  commitId: string;
  draft: boolean;
  locked?: boolean;
};

export type GitRepositoryEntry = {
  name?: string;
  path?: string;
  type?: string;
  size?: number;
  commitId?: string;
};

export type GitCommitEntry = {
  id?: string;
  message?: string;
  author?: string;
  authorEmail?: string;
  createdDate?: string;
};

export type GitCredentials = {
  userName?: string;
  token?: string;
};

export type CheckRepositoryRequest = {
  repository?: string;
  repositorySsh?: string;
  repositoryType?: RepositoryType;
  repositoryToken?: string;
};

export type PipelineSourceItemVO = {
  path?: string;
  content?: string;
  version?: string;
};

export type PipelineSourceItemsVO = {
  items?: PipelineSourceItemVO[];
};

export type RegisterPipelineVersionVO = {
  pipelineId?: number;
  version?: string;
  description?: string;
};

export type {Pipeline, ConfigurationEntry, ConfigurationParameters};
