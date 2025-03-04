import { AclClass, AclEntry } from './acl';
import { PipelineParametersTypes, PipelineType, RepositoryType } from './enums';
import { NgsData } from './misc';

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
  data?: NgsData;
};

export type PipelineVersion = {
  id: number;
  name: string;
  message?: string;
  createdDate: string;
  draft: boolean;
  commitId?: string;
  author: string;
  authorEmail?: string;
};

export type PipelineParameter = {
  visible?: string;
  value: string | boolean;
  type: PipelineParametersTypes;
  required: boolean;
  no_override?: boolean;
  enum?: string[];
  description?: string;
  pretty_name?: string;
  section?: string;
};

export type MappedPipelineParameter = {
  key: string;
  initialKey: string;
  value: string | boolean;
  pretty_name: string;
  touched: boolean;
  markAsDeleted: boolean;
  error?: string;
  keyError?: string;
  section: string;
  initial: PipelineParameter;
  isSystemParameter: boolean;
};

export type PipelineConfiguration = {
  name: string;
  description?: string;
  configuration: {
    nonPause: boolean;
    cloudRegionId: number;
    main_file: string;
    instance_size: string;
    instance_disk: string;
    docker_image: string;
    timeout: number;
    cmd_template: string;
    language: string;
    parameters: Record<string, PipelineParameter>;
    is_spot: boolean;
    raw: boolean;
    notifications?: Array<Record<string, unknown>>;
  };
  default: boolean;
};

export type PipelineFile = {
  id: string;
  mode: string;
  name: string;
  path: string;
  type: string;
};

export type PipelineInfo = Pipeline & {
  currentVersion: PipelineVersion;
};

export type PipelineVersionParameters = {
  cmd_template?: string;
  docker_image?: string;
  instance_disk?: string;
  instance_size?: string;
  language?: string;
  main_file?: string;
  nonPause?: boolean;
};
