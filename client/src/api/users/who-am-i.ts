import {UserInfo} from '../../@types/users.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function whoAmI(): Promise<UserInfo> {
  return cloudPipelineApi.jsonGet<UserInfo>({
    uri: 'whoami',
  });
}
