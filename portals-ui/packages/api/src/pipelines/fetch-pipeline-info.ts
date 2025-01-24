import { PipelineInfo } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelineInfo(
  pipelineId: number,
  abortSignal?: AbortSignal,
): Promise<PipelineInfo> {
  return await cloudPipelineApi.jsonGet<PipelineInfo>({
    uri: `/pipeline/${pipelineId}/load`,
    signal: abortSignal,
  });
}
