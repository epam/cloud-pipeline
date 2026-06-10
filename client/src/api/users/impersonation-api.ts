import type {UserInfo} from '../../@types/users.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export type ImpersonationInfo = {
  original?: UserInfo;
  impersonated?: UserInfo;
};

export async function loadImpersonation(): Promise<ImpersonationInfo> {
  return cloudPipelineApi.jsonGet<ImpersonationInfo>({uri: 'user/impersonation'});
}

export async function stopImpersonationRequest(): Promise<void> {
  await cloudPipelineApi.jsonGet({uri: 'user/impersonation/stop'});
}
