import { Pipeline } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelines(): Promise<Pipeline[]> {
  return await cloudPipelineApi.jsonGet<Pipeline[]>({
    uri: 'pipeline/loadAll',
  });
}
