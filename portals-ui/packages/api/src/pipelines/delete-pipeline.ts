import { Pipeline } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function deletePipeline(pipelineId: number): Promise<Pipeline> {
  const result = await cloudPipelineApi.jsonDelete<Pipeline>({
    uri: `/pipeline/${pipelineId}/delete`,
  });
  return result;
}
