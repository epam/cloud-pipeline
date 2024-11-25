import { Run, RunFilters, RunsResponse } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

const defaultFilters = {
  page: 1,
  pageSize: 20,
};

export async function fetchRuns(
  filters: RunFilters = defaultFilters,
): Promise<Run[]> {
  const result = await cloudPipelineApi.jsonPost<RunsResponse>({
    uri: 'run/filter',
    body: filters,
  });
  return result?.elements ?? [];
}
