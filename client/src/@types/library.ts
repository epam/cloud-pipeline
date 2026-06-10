import type {MaskedObject} from './common.ts';

export type LibraryAclClass = 'FOLDER' | 'PIPELINE' | 'DATA_STORAGE' | 'CONFIGURATION';

export type RepositoryType =
  | 'GITLAB'
  | 'GITHUB'
  | 'BITBUCKET'
  | 'BITBUCKET_CLOUD'
  | 'AZURE_DEVOPS'
  | 'GITHUB_APP';

export type PipelineType = 'PIPELINE' | 'VERSIONED_STORAGE';

export type DataStorageType = 'S3' | 'NFS' | 'AZ' | 'GS' | 'AWS_OMICS_REF' | 'AWS_OMICS_SEQ';

export type RunVisibilityPolicy = 'INHERIT' | 'OWNER';

export type ExecutionEnvironment = 'CLOUD_PLATFORM' | 'FIRECLOUD' | 'DTS';

export type RunAccessType = 'ENDPOINT' | 'SSH';

export type LibraryEntityBase<AclClass extends LibraryAclClass> = MaskedObject & {
  id: number;
  name: string;
  createdDate: string;
  owner: string;
  locked?: boolean;
  aclClass: AclClass;
};

export type LibraryParentRef = Partial<MaskedObject> & {
  id: number;
  createdDate?: string;
  locked?: boolean;
  aclClass: LibraryAclClass;
  hasMetadata?: boolean;
  name?: string;
};

export type StoragePolicy = {
  versioningEnabled?: boolean;
  backupDuration?: number;
  shortTermStorageDuration?: number;
  longTermStorageDuration?: number;
  incompleteUploadCleanupDays?: number;
};

export type DataStorageToolToMount = {
  id: number;
  registry: string;
  image: string;
  versions?: Array<{version: string}>;
};

export type RunSid = {
  runId?: number;
  name?: string;
  isPrincipal?: boolean;
  accessType?: RunAccessType;
};

export type ConfigurationParameter = {
  value?: string;
  type?: string;
  required?: boolean;
  multiple?: boolean;
  no_override?: boolean;
  pretty_name?: string;
  icon?: string;
  section?: string;
  read_only?: boolean;
  enum?: unknown[];
  validation?: Array<Record<string, string>>;
  annotation?: Record<string, unknown>;
  scheme?: Record<string, unknown>;
  metadata_config?: Record<string, unknown>;
  visible?: string;
  description?: string;
};

export type ConfigurationParameters = Record<string, ConfigurationParameter>;

export type PipelineConfiguration = {
  main_file?: string;
  main_class?: string;
  instance_size?: string;
  instance_image?: string;
  instance_disk?: string;
  docker_image?: string;
  timeout?: number;
  cmd_template?: string;
  language?: string;
  node_count?: number;
  worker_cmd?: string;
  parameters?: ConfigurationParameters;
  is_spot?: boolean;
  nonPause?: boolean;
  cloudRegionId?: number;
  share_with_users?: RunSid[];
  share_with_roles?: RunSid[];
  run_as?: string;
  friendly_url?: string;
  raw?: boolean;
  conditional_parameters?: Record<string, unknown>;
  config_description?: string;
  tags?: Record<string, string>;
  kubeLabels?: Record<string, string>;
};

export type ConfigurationEntryBase = {
  executionEnvironment?: ExecutionEnvironment;
  name?: string;
  rootEntityId?: number;
  runSids?: RunSid[];
  configName?: string;
  default?: boolean;
  endpointName?: string;
  stopAfter?: number;
};

export type CloudPlatformConfigurationEntry = ConfigurationEntryBase & {
  executionEnvironment?: 'CLOUD_PLATFORM';
  pipelineId?: number;
  pipelineVersion?: string;
  configuration?: PipelineConfiguration;
};

export type ConfigurationEntry = CloudPlatformConfigurationEntry;

type PipelineRevision = {
  id: number;
  name: string;
  createdDate: string;
  description?: string;
  author?: string;
  commitId: string;
  draft: boolean;
  locked?: boolean;
};

export type Pipeline = LibraryEntityBase<'PIPELINE'> & {
  description?: string;
  repository?: string;
  repositorySsh?: string;
  parentFolderId?: number;
  parent?: LibraryParentRef;
  templateId?: string;
  repositoryType?: RepositoryType;
  repositoryToken?: string;
  pipelineType?: PipelineType;
  repositoryError?: string;
  hasMetadata?: boolean;
  branch?: string;
  configurationPath?: string;
  visibility?: RunVisibilityPolicy;
  codePath?: string;
  docsPath?: string;
  issuesCount?: number;
  objectMetadata?: Record<string, unknown>;
  currentVersion?: PipelineRevision;
};

export type DataStorage = LibraryEntityBase<'DATA_STORAGE'> & {
  description?: string;
  path?: string;
  type?: DataStorageType;
  parentFolderId?: number;
  parent?: LibraryParentRef;
  storagePolicy?: StoragePolicy;
  hasMetadata?: boolean;
  fileShareMountId?: number;
  mountPoint?: string;
  mountOptions?: string;
  mountExactPath?: boolean;
  shared?: boolean;
  sensitive?: boolean;
  mountDisabled?: boolean;
  linkingMasks?: string[];
  sourceStorageId?: number;
  pathPermissionsEnabled?: boolean;
  regionId?: number;
  useAssumedCredentials?: boolean;
  tempCredentialsRole?: string;
  kmsKeyArn?: string;
  allowedCidrs?: string[];
  delimiter?: string;
  pathMask?: string;
  root?: string;
  policySupported?: boolean;
  issuesCount?: number;
  objectMetadata?: Record<string, unknown>;
  prefix?: string;
  toolsToMount?: DataStorageToolToMount[];
};

export type Configuration = LibraryEntityBase<'CONFIGURATION'> & {
  description?: string;
  parent?: LibraryParentRef;
  entries?: ConfigurationEntry[];
};

export type Folder = LibraryEntityBase<'FOLDER'> & {
  parentId?: number;
  childFolders?: Folder[];
  pipelines?: Pipeline[];
  storages?: DataStorage[];
  configurations?: Configuration[];
  metadata?: Record<string, number>;
  hasMetadata?: boolean;
  description?: string;
  issuesCount?: number;
  objectMetadata?: Record<string, unknown>;
};

export type LibraryRootFolder = Omit<Folder, keyof LibraryEntityBase<LibraryAclClass>> & {
  // Forbid entity identity fields so Folder is not assignable here; otherwise
  // Exclude<LibraryEntity | LibraryRootFolder, LibraryRootFolder> drops Folder.
  [K in keyof LibraryEntityBase<LibraryAclClass>]?: never;
};

export type LibraryEntity = Folder | Pipeline | DataStorage | Configuration;
