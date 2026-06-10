import {Preference} from '../../@types/preferences.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function updatePreferences(preferences: Preference[]): Promise<void> {
  await cloudPipelineApi.jsonPost({
    uri: 'preferences',
    body: preferences,
  });
}
