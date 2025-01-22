import { RunLog } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchRunLogs(
  runId: number,
  taskName?: string,
): Promise<RunLog[]> {
  const params = new URLSearchParams({ taskName: `${taskName}` }).toString();
  const query = taskName ? `?${params}` : '';
  const result = await cloudPipelineApi.jsonGet<RunLog[]>({
    uri: `run/${runId}/task${query}`,
  });
  return result;
}
