import { cloudPipelineApi } from '@cloud-pipeline/api';
import type { User } from '@cloud-pipeline/core';
import { authenticate } from '../authentication/authenticate.ts';
import fetchSettings from '../../shared/settings/fetch-settings.ts';
import { settingsStore } from '../settings/store.ts';

export type InitializeApplicationState = {
  message?: string;
  details?: string;
};

export type InitializeCallback = (state: InitializeApplicationState) => void;

export async function initialize(callback?: InitializeCallback): Promise<User> {
  if (callback) {
    callback({ message: 'Fetching settings...' });
  }
  const settings = await fetchSettings();
  settingsStore.setState({ settings });
  cloudPipelineApi.initialize({
    base: settings.api,
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
