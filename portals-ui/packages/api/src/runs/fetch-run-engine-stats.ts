import { EngineTasks } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchRunEngineStats(runId: number, engineType = 'NEXTFLOW'): Promise<EngineTasks> {
  return await cloudPipelineApi.jsonGet<EngineTasks>({
    uri: `/run/${runId}/engine/${engineType}/tasks/stats`,
  });
}
