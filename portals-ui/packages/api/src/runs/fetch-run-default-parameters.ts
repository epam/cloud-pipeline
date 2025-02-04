import { RunDefaultParameter } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchRunDefaultParameters(): Promise<
  RunDefaultParameter[]
> {
  const result = await cloudPipelineApi.jsonGet<RunDefaultParameter[]>({
    uri: `run/defaultParameters`,
  });
  return result;
}
