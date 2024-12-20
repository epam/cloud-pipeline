import {
  AclClass,
  NgsData,
  UpdateProjectMetadataResponse,
} from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function updateProjectMetadata(
  data: NgsData,
  projectId: number,
): Promise<NgsData> {
  const result = await cloudPipelineApi.jsonPost<UpdateProjectMetadataResponse>(
    {
      uri: 'metadata/update',
      body: {
        data,
        entity: {
          entityClass: AclClass.folder,
          entityId: projectId,
        },
      },
    },
  );
  return result.data;
}
