import { Run, RunFilters, RunsResponse } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

const DEFAULT_FILTERS = {
  eagerGrouping: false,
  statuses: [],
  owners: [],
  tags: {},
  page: 1,
  pageSize: 20,
  userModified: false,
};

export async function fetchRuns(filters: RunFilters = {}): Promise<Run[]> {
  try {
    const result = await cloudPipelineApi.jsonPost<RunsResponse>({
      uri: 'run/filter',
      body: Object.assign(DEFAULT_FILTERS, filters),
    });
    return result?.elements ?? [];
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    throw new Error(errorMessage);
  }
}
