import cloudPipelineApi from '../cloud-pipeline-api';
import { RunTaskDetails, RunTaskDetailsType, RunTaskDetailsContentType } from '@cloud-pipeline/core';

type Payload = {
  runId: number;
  hash: string;
  contentType: RunTaskDetailsContentType;
  taskType?: RunTaskDetailsType;
};

export async function fetchRunEngineTaskDetails(payload: Payload): Promise<RunTaskDetails> {
  const { runId, hash, contentType, taskType = 'NF_TASK' } = payload;

  const body = {
    hash,
    type: contentType,
  };

  return await cloudPipelineApi.jsonPost<RunTaskDetails>({
    uri: `/run/${runId}/runtime/data?type=${taskType}`,
    body,
  });
}
