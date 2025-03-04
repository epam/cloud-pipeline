import { PipelineLanguages } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelineLanguage(
  id: number,
  version: string,
  abortSignal?: AbortSignal,
): Promise<PipelineLanguages> {
  return await cloudPipelineApi.jsonGet<PipelineLanguages>({
    uri: `/pipeline/${id}/language?version=${version}`,
    signal: abortSignal,
  });
}
