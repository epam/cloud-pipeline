import { DataStorage } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchAvailableDataStorages(): Promise<DataStorage[]> {
  return await cloudPipelineApi.jsonGet<DataStorage[]>({
    uri: 'datastorage/available',
  });
}
