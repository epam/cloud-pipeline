import { Pipeline } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelines(
  loadVersion = false,
  loadMetadata = true,
): Promise<Pipeline[]> {
  return await cloudPipelineApi.jsonPost<Pipeline[]>({
    uri: `pipeline/filter?loadVersion=${loadVersion}&loadMetadata=${loadMetadata}`,
    body: {},
  });
}
