import { PipelineVersion } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelineVersions(
  pipelineId: number,
): Promise<PipelineVersion[]> {
  return await cloudPipelineApi.jsonGet<PipelineVersion[]>({
    uri: `/pipeline/${pipelineId}/versions`,
  });
}
