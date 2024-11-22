import { User } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchAuthenticatedUser(): Promise<User> {
  return await cloudPipelineApi.jsonGet<User>({
    uri: 'whoami',
  });
}
