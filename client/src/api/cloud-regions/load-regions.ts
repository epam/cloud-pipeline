import {CloudRegion} from '../../@types/regions.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadCloudRegions(): Promise<CloudRegion[]> {
  return cloudPipelineApi.jsonGet<CloudRegion[]>({uri: 'cloud/region'});
}

export async function loadCloudRegion(id: number): Promise<CloudRegion> {
  return cloudPipelineApi.jsonGet<CloudRegion>({uri: `cloud/region/${id}`});
}

export async function loadAvailableCloudRegions(): Promise<CloudRegion[]> {
  return cloudPipelineApi.jsonGet<CloudRegion[]>({uri: 'cloud/region/available'});
}
