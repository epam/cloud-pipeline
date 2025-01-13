import { AclClass } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api/index.ts';

export type PermissionsResponse = {
  entityId: number;
  entityClass: AclClass;
  owner: string;
  permissions: {
    sid: {
      name: string;
      principal: boolean;
    };
    mask: number;
  }[];
};

export async function fetchPermissions(
  id: number,
  aclClass: AclClass,
): Promise<PermissionsResponse> {
  const query = new URLSearchParams({ id: `${id}`, aclClass }).toString();

  return await cloudPipelineApi.jsonGet<PermissionsResponse>({
    uri: `permissions?${query}`,
  });
}
