import { RunTask } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchRunTasks(runId: number): Promise<RunTask[]> {
  const result = await cloudPipelineApi.jsonGet<RunTask[]>({
    uri: `run/${runId}/tasks`,
  });
  return result;
}
