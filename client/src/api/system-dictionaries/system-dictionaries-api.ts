import {SystemDictionary} from '../../@types/system-dictionaries.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadSystemDictionaries(): Promise<SystemDictionary[]> {
  return cloudPipelineApi.jsonGet<SystemDictionary[]>({uri: 'categoricalAttribute'});
}
