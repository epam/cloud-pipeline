import { Run, RunStatuses } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function stopRun(runId: number, endDate: string): Promise<Run> {
  const run = await cloudPipelineApi.jsonPost<Run>({
    uri: `/run/${runId}/status`,
    body: {
      endDate: endDate,
      status: RunStatuses.stopped,
    },
  });
  return run;
}
