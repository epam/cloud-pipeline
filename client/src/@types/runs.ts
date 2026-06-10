import type {MaskedObject} from './common.ts';
import type {
  ConfigurationParameters,
  ExecutionEnvironment,
  RunAccessType,
  RunSid,
} from './library.ts';

export type TaskStatus =
  | 'SUCCESS'
  | 'FAILURE'
  | 'RUNNING'
  | 'STOPPED'
  | 'PAUSING'
  | 'PAUSED'
  | 'RESUMING';

export type CommitStatus = 'NOT_COMMITTED' | 'COMMITTING' | 'FAILURE' | 'SUCCESS';

export type CloudProvider = 'AWS' | 'AZURE' | 'GCP' | 'LOCAL';

export type RunAclClass = 'PIPELINE' | 'TOOL';

export type FilterExpressionType = 'LOGICAL' | 'AND' | 'OR';

export type OffsetPagingOrder = 'ASC' | 'DESC';

export type PipelineRunParameter = {
  name?: string;
  value?: string;
  type?: string;
  resolvedValue?: string;
};

export type RunInstance = {
  nodeType?: string;
  nodeDisk?: number;
  nodeIP?: string;
  nodeId?: string;
  nodeImage?: string;
  nodeName?: string;
  spot?: boolean;
  cloudRegionId?: number;
  cloudProvider?: CloudProvider;
};

export type ExecutionPreferences = {
  environment?: ExecutionEnvironment;
  method?: string;
  methodSnapshot?: string;
  methodConfiguration?: string;
  dtsId?: number;
  coresNumber?: number;
};

export type PipelineTask = {
  name?: string;
  status?: TaskStatus;
  instance?: string;
  created?: string;
  started?: string;
  finished?: string;
};

export type PipelineRun = MaskedObject & {
  id?: number;
  name?: string;
  createdDate?: string;
  owner?: string;
  locked?: boolean;
  pipelineId?: number;
  startDate?: string;
  endDate?: string;
  status?: TaskStatus;
  commitStatus?: CommitStatus;
  params?: string;
  dockerImage?: string;
  cmdTemplate?: string;
  serviceUrl?: Record<string, string>;
  pipelineName?: string;
  pipelineRunParameters?: PipelineRunParameter[];
  instance?: RunInstance;
  timeout?: number;
  version?: string;
  configName?: string;
  nodeCount?: number;
  parentRunId?: number;
  configurationId?: number;
  runSids?: RunSid[];
  executionPreferences?: ExecutionPreferences;
  prettyUrl?: string;
  nonPause?: boolean;
  aclClass?: RunAclClass;
  tags?: Record<string, string>;
};

export type RunLog = {
  runId?: number;
  date?: string;
  status?: TaskStatus;
  taskName?: string;
  logText?: string;
  instance?: string;
  task?: PipelineTask;
};

export type PipelineStart = {
  pipelineId?: number;
  version?: string;
  timeout?: number;
  instanceType?: string;
  hddSize?: number;
  dockerImage?: string;
  cmdTemplate?: string;
  configurationName?: string;
  nodeCount?: number;
  workerCmd?: string;
  parentRunId?: number;
  isSpot?: boolean;
  runSids?: RunSid[];
  cloudRegionId?: number;
  force?: boolean;
  executionEnvironment?: ExecutionEnvironment;
  prettyUrl?: string;
  nonPause?: boolean;
  params?: ConfigurationParameters;
  tags?: Record<string, string>;
};

export type PipelineRunFilter = {
  regionIds?: number[];
  pipelineIds?: number[];
  versions?: string[];
  statuses?: TaskStatus[];
  startDateFrom?: string;
  endDateTo?: string;
  owners?: string[];
  roles?: string[];
  configurationIds?: number[];
  projectIds?: number[];
  tags?: Record<string, string>;
};

export type PagingRunFilter = PipelineRunFilter & {
  page?: number;
  pageSize?: number;
};

export type FilterExpression = {
  field?: string;
  value?: string;
  operand?: string;
  expressions?: FilterExpression[];
  filterExpressionType?: FilterExpressionType;
};

export type PagingRunFilterExpression = {
  page?: number;
  pageSize?: number;
  timezoneOffsetInMinutes?: number;
  filterExpression?: FilterExpression;
};

export type FilterField = {
  fieldName?: string;
  fieldDescription?: string;
  supportedOperands?: string[];
  regex?: boolean;
};

export type PagedRunsResult = {
  elements: PipelineRun[];
  totalCount: number;
};

export type {RunSid, RunAccessType};
