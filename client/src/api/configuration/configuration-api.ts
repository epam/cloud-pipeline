import type {Configuration} from '../../@types/library.ts';
import type {RunConfigurationVO} from '../../@types/configuration.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadConfiguration(id: number): Promise<Configuration> {
  return cloudPipelineApi.jsonGet<Configuration>({uri: `configuration/${id}`});
}

export async function loadAllConfigurations(): Promise<Configuration[]> {
  return cloudPipelineApi.jsonGet<Configuration[]>({uri: 'configuration/loadAll'});
}

export async function saveConfiguration(configuration: RunConfigurationVO): Promise<Configuration> {
  return cloudPipelineApi.jsonPost<Configuration>({uri: 'configuration', body: configuration});
}

export async function deleteConfiguration(id: number): Promise<Configuration> {
  return cloudPipelineApi.jsonDelete<Configuration>({uri: `configuration/${id}`});
}
