import { AclClass, AclEntry } from './acl';
import { CloudProviders, CommitStatuses, RunStatuses } from './enums';
import { Dayjs } from 'dayjs';

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

export type Run = AclEntry<AclClass.pipeline> & {
  id: number;
  createdDate: string;
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

export type RunTaskInfo = {
  name?: string;
  started?: string;
};

export enum RunHistoryPhase {
  scheduled = 0,
  running = 1,
  paused = 2,
  stopped = 3,
}

export type RunInterval = {
  phase: RunHistoryPhase;
  start: Dayjs;
  end?: Dayjs;
};
