import { AclClass } from './acl';
import { CloudProviders, CommitStatuses, RunStatuses } from './enums';

export type RunsResponse = {
  elements: Run[];
  totalCount: number;
};

type RunParameter = {
  dataStorageLinks?: string[];
  name?: string;
  resolvedValue?: string;
  type?: string;
  value?: string;
};

type RunInstance = {
  cloudProvider?: CloudProviders;
  cloudRegionId?: number;
  effectiveNodeDisk?: number;
  nodeDisk?: number;
  nodeIP?: string;
  nodeId?: string;
  nodeImage?: string;
  nodeName?: string;
  nodePlatform?: string;
  nodeType?: string;
  poolId?: number;
  prePulledDockerImages?: string[];
  spot?: boolean;
};

type RunDetailedStatus = {
  reason: string;
  runId: number;
  status: RunStatuses;
  timestamp: string;
};

export type Run = {
  id: number;
  createdDate: string;
  mask: number;
  owner: string;
  locked: boolean;
  originalOwner: string;
  startDate: string;
  instanceStartDate?: string;
  version?: string;
  endDate?: string;
  status: RunStatuses;
  commitStatus: CommitStatuses;
  lastChangeCommitTime: string;
  params?: string;
  dockerImage: string;
  actualDockerImage: string;
  platform: string;
  cmdTemplate: string;
  actualCmd: string;
  terminating: boolean;
  sensitive: boolean;
  podId: string;
  pipelineName?: string;
  pipelineRunParameters?: RunParameter[];
  instance: RunInstance;
  timeout: number;
  configName?: string;
  nodeCount: number;
  initialized: boolean;
  configurationId: number;
  prolongedAtTime: string;
  executionPreferences?: Record<string, any>;
  pricePerHour: number;
  computePricePerHour: number;
  diskPricePerHour: number;
  runStatuses?: RunDetailedStatus[];
  nonPause: boolean;
  aclClass: AclClass.pipeline;
  kubeServiceEnabled: boolean;
  workerRun: boolean;
  clusterRun: boolean;
  taskName: string;
  masterRun: boolean;
  podIP?: string;
  podStatus?: string;
  tags?: Record<string, string>;
  serviceUrl?: Record<string, string>;
  childRuns?: Run[];
  workersPrice?: number;
  stateReasonMessage?: string;
  queued?: boolean;
  lastIdleNotificationTime?: string;
  lastNotificationTime?: string;
  pipelineId?: number;
  entitiesIds?: number[];
};

export type RunFilters = {
  page?: number;
  pageSize?: number;
  startDateFrom?: string;
  endDateTo?: string;
  roles?: string[];
  statuses?: RunStatuses[];
  configurationIds?: number[];
  dockerImages?: string[];
  tags?: Record<string, string>;
  entitiesIds?: number[];
  instanceTypes?: string[];
  owners?: string[];
  parentId?: number;
  partialParameters?: string;
  pipelineIds?: number[];
  prettyUrl?: string;
  projectIds?: number[];
  regionIds?: number[];
  masterRun?: boolean;
  ownershipFilter?: string;
  userModified?: boolean;
  versions?: string[];
  workerRun?: boolean;
  eagerGrouping?: boolean;
};
