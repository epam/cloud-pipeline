import { PipelineVersionParameters } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelineVersionParameters(
  id: number,
  version: string,
  abortSignal?: AbortSignal,
): Promise<PipelineVersionParameters> {
  return await cloudPipelineApi.jsonGet<PipelineVersionParameters>({
    uri: `/pipeline/${id}/parameters?version=${version}`,
    signal: abortSignal,
  });
}
