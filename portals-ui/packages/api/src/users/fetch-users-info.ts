import { UserInfo } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchUsersInfo(): Promise<UserInfo[]> {
  return await cloudPipelineApi.jsonGet<UserInfo[]>({
    uri: 'users/info',
  });
}
