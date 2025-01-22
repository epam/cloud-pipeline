import { Run } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchRun(runId: number): Promise<Run> {
  const result = await cloudPipelineApi.jsonGet<Run>({
    uri: `run/${runId}`,
  });
  return result;
}
