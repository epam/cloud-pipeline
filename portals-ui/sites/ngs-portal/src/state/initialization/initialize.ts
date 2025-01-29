import type { InitializationMessage } from './types.ts';
import { authenticationStore } from '../authentication/store.ts';
import { initializeCloudPipelineApi } from './initialize-cp-api.ts';

export type InitializeCallback = (state: InitializationMessage) => void;

export async function initialize(
  callback?: InitializeCallback,
): Promise<boolean> {
  if (callback) {
    callback({ message: 'Initializing...', details: 'fetching settings' });
  }
  await initializeCloudPipelineApi();
  if (callback) {
    callback({ message: 'Authenticating...' });
  }
  const user = await authenticationStore.getState().load();
  if (!user) {
    throw new Error(
      authenticationStore.getState().error ?? 'Authentication failed',
    );
  }
  if (callback) {
    callback({ message: 'User authenticated' });
  }
  return true;
}
