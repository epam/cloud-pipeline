import {Preference} from '../../@types/preferences.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function fetchPreference(preference: string): Promise<Preference> {
  try {
    return cloudPipelineApi.jsonGet<Preference>({uri: `preferences/${preference}`});
  } catch (error) {
    console.warn(`Error fetching preference ${preference}`, error);
    throw error;
  }
}
