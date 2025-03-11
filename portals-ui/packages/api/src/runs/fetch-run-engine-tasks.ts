import cloudPipelineApi from '../cloud-pipeline-api';
import { RunTasksData, Sorting } from '@cloud-pipeline/core';

type Payload = {
  runId: number;
  filter?: {
    page?: number;
    pageSize?: number;
    taskGroup?: string;
    sorting?: Sorting;
  };
  engineType?: string;
};

export async function fetchRunEngineTasks(payload: Payload): Promise<RunTasksData> {
  const { runId, filter, engineType = 'NEXTFLOW' } = payload;

  const body = {
    page: filter?.page ?? 1,
    pageSize: filter?.pageSize ?? 25,
    taskGroup: filter?.taskGroup,
    sorts: filter?.sorting ? [filter?.sorting] : undefined,
  };

  return await cloudPipelineApi.jsonPost<RunTasksData>({
    uri: `/run/${runId}/engine/${engineType}/tasks/filter`,
    body,
  });
}
