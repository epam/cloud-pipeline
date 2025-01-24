import { AclClass } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

type UserMetadataResponseRaw = Array<{
  data: Record<string, Record<string, unknown>>;
  entity: {
    entityId: number;
    entityClass: AclClass;
  };
}>;

export type UserMetadataResponse = Record<string, Record<string, unknown>>;

export async function fetchUserMetadata(
  userId: number,
): Promise<UserMetadataResponse> {
  const response = await cloudPipelineApi.jsonPost<UserMetadataResponseRaw>({
    uri: 'metadata/load',
    body: [
      {
        entityClass: AclClass.pipelineUser,
        entityId: userId,
      },
    ],
  });
  return response[0]?.data;
}
