import { fetchAuthenticatedUser, fetchUserMetadata } from '@cloud-pipeline/api';
import type { AuthenticatedUserInfo } from './types';
import { initializeCloudPipelineApi } from '../initialization/initialize-cp-api';

/**
 * Returns authenticated user with metadata or throws an error
 */
export async function authenticate(
  abortSignal?: AbortSignal,
): Promise<AuthenticatedUserInfo> {
  if (abortSignal?.aborted) {
    throw new Error('Authentication aborted');
  }
  await initializeCloudPipelineApi();
  const user = await fetchAuthenticatedUser();
  if (abortSignal?.aborted) {
    throw new Error('Authentication aborted');
  }
  const metadata = await fetchUserMetadata(user.id);
  return {
    user,
    metadata,
  };
}
