import { createSingleCallPromise } from '@cloud-pipeline/core';
import fetchSettings from '../../shared/settings/fetch-settings.ts';
import { settingsStore } from '../settings/store.ts';
import { cloudPipelineApi } from '@cloud-pipeline/api';

export const initializeCloudPipelineApi = createSingleCallPromise(async () => {
  const settings = await fetchSettings();
  settingsStore.setState({ settings });
  cloudPipelineApi.initialize({
    base: settings.api,
  });
});
