import { PipelineConfiguration } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelineConfigurations(
  pipelineId: number,
  version: string,
): Promise<PipelineConfiguration[]> {
  return await cloudPipelineApi.jsonGet<PipelineConfiguration[]>({
    uri: `/pipeline/${pipelineId}/configurations?version=${version}`,
  });
}
