import {Preference} from '../../@types/preferences.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function fetchPreferences(): Promise<Preference[]> {
  return cloudPipelineApi.jsonGet<Preference[]>({uri: `preferences`});
}
