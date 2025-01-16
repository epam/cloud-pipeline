import { LaunchPayload } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function launchPipeline(
  payload: LaunchPayload,
): Promise<LaunchPayload> {
  const result = await cloudPipelineApi.jsonPost<LaunchPayload>({
    uri: 'run',
    body: payload,
  });
  return result;
}
