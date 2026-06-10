import type {MaskedObject} from './common.ts';

export type ToolAclClass = 'DOCKER_REGISTRY' | 'TOOL' | 'TOOL_GROUP';

export type SecuredEntityBase = MaskedObject & {
  id: number;
  name: string;
  createdDate?: string;
  owner?: string;
  locked?: boolean;
};

export type ToolGroupRef = Partial<MaskedObject> & {
  id: number;
  aclClass?: 'TOOL_GROUP';
};

export type Tool = MaskedObject & {
  id: number;
  image: string;
  owner: string;
  cpu?: string;
  ram?: string;
  instanceType?: string;
  disk?: number;
  registryId: number;
  registry: string;
  registryRef: DockerRegistry;
  toolGroupId?: number;
  toolGroup?: string;
  toolGroupRef?: ToolGroup;
  description?: string;
  shortDescription?: string;
  labels?: string[];
  endpoints?: string[];
  defaultCommand?: string;
  hasIcon?: boolean;
  iconId?: number;
  aclClass?: 'TOOL';
};

export type ToolGroup = SecuredEntityBase & {
  registryId?: number;
  description?: string;
  tools?: Tool[];
  privateGroup?: boolean;
  aclClass?: 'TOOL_GROUP';
};

export type DockerRegistry = SecuredEntityBase & {
  path: string;
  description?: string;
  tools?: Tool[];
  groups?: ToolGroup[];
  pipelineAuth?: boolean;
  hasMetadata?: boolean;
  externalUrl?: string;
  securityScanEnabled?: boolean;
  privateGroupAllowed?: boolean | null;
  aclClass?: 'DOCKER_REGISTRY';
};

export type DockerRegistryVO = {
  id?: number;
  path?: string;
  description?: string;
  userName?: string;
  password?: string;
  caCert?: string;
  pipelineAuth?: boolean;
  externalUrl?: string;
  securityScanEnabled?: boolean;
};

export type DockerRegistryList = {
  registries?: DockerRegistry[];
};

export type ToolSymlinkRequest = {
  toolId: number;
  groupId: number;
};

export type ToolVersion = {
  id?: number;
  version?: string;
  image?: string;
  platform?: string;
  digest?: string;
};

export type ToolDescription = {
  description?: string;
  shortDescription?: string;
};
