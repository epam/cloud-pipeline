import { PipelineFile } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelineFiles(
  pipelineId: number,
  version: string,
): Promise<PipelineFile[]> {
  return await cloudPipelineApi.jsonGet<PipelineFile[]>({
    uri: `/pipeline/${pipelineId}/docs?version=${version}`,
  });
}
