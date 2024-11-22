import { cloudPipelineApi } from '@cloud-pipeline/api';
import type { User } from '@cloud-pipeline/core';
import { authenticate } from '../authentication/authenticate.ts';

export type InitializeApplicationState = {
  message?: string;
  details?: string;
};

export type InitializeCallback = (state: InitializeApplicationState) => void;

export async function initialize(callback?: InitializeCallback): Promise<User> {
  cloudPipelineApi.initialize({
    base: CLOUD_PIPELINE_API,
  });
  if (callback) {
    callback({ message: 'Authenticating...' });
  }
  const user = await authenticate();
  if (callback) {
    callback({ message: 'User authenticated' });
  }
  return user;
}
