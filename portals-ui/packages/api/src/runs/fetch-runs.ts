import { Run, RunStatuses } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';
import { PagedRequest, PagedResponse } from '../types.common.ts';

export type RunsCompleteFilter = {
  startDateFrom: string;
  endDateTo: string;
  roles: string[];
  statuses: RunStatuses[];
  configurationIds: number[];
  dockerImages: string[];
  tags: Record<string, string>;
  entitiesIds: number[];
  instanceTypes: string[];
  owners: string[];
  parentId: number;
  partialParameters: string;
  pipelineIds: number[];
  prettyUrl: string;
  projectIds: number[];
  regionIds: number[];
  masterRun: boolean;
  ownershipFilter: string;
  userModified: boolean;
  versions: string[];
  workerRun: boolean;
  eagerGrouping: boolean;
};

export type RunsFilter = Partial<RunsCompleteFilter> & PagedRequest;

type RunsResponseRaw = {
  elements: Run[];
  totalCount: number;
};

export type RunsResponse = PagedResponse & {
  runs: Run[];
};

export async function fetchRuns(filters: RunsFilter): Promise<RunsResponse> {
  const { page, pageSize = 10, ...rest } = filters ?? {};
  const { elements, totalCount } =
    await cloudPipelineApi.jsonPost<RunsResponseRaw>({
      uri: 'run/filter',
      body: {
        page,
        pageSize,
        ...rest,
      },
    });
  return {
    runs: elements,
    page,
    pageSize,
    total: totalCount,
  };
}
