import { LaunchPayload, Run } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function launchPipeline(payload: LaunchPayload): Promise<Run> {
  const result = await cloudPipelineApi.jsonPost<Run>({
    uri: 'run',
    body: payload,
  });
  return result;
}
