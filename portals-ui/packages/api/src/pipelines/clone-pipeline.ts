import { Pipeline } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function clonePipeline(
  pipelineId: number,
  name: string,
  parentId?: number,
): Promise<Pipeline> {
  const query = [
    parentId !== undefined ? `parentId=${parentId}` : null,
    `name=${encodeURIComponent(name)}`,
  ]
    .filter(Boolean)
    .join('&');
  const result = await cloudPipelineApi.jsonPost<Pipeline>({
    uri: `/pipeline/${pipelineId}/copy?${query}`,
  });
  return result;
}
