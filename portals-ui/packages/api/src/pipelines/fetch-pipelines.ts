import { Pipeline } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export type FetchPipelinesOptions = {
  loadVersion?: boolean;
  loadMetadata?: boolean;
  abortSignal?: AbortSignal;
};

export async function fetchPipelines(
  options?: FetchPipelinesOptions,
): Promise<Pipeline[]> {
  const {
    loadVersion = false,
    loadMetadata = true,
    abortSignal,
  } = options ?? {};
  return await cloudPipelineApi.jsonPost<Pipeline[]>({
    uri: `pipeline/filter?loadVersion=${loadVersion}&loadMetadata=${loadMetadata}`,
    body: {},
    signal: abortSignal,
  });
}
