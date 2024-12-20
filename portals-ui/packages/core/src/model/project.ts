import { AclClass, AclEntry } from './acl';
import { NgsData } from './misc';
import { Pipeline } from './pipeline';

export type ProjectsResponse = {
  childFolders: Project[];
  createdDate: string;
  mask: number;
  locked: boolean;
};

export type UpdateProjectMetadataResponse = {
  entity: {
    entityId: number;
    entityClass: AclClass.folder;
  };
  data: NgsData;
};

export type Configuration = {
  id: number;
  name: string;
  createdDate: string;
  mask: number;
  owner: string;
  locked: boolean;
  parent?: {
    id: number;
    createdDate: string;
    mask: number;
    locked: boolean;
    aclClass: AclClass.folder;
    hasMetadata: boolean;
  };
  entries: Record<string, any>[];
};

export type Project = AclEntry<AclClass.folder> & {
  id: number;
  name: string;
  createdDate: string;
  locked: boolean;
  parentId?: number;
  pipelines?: Pipeline[];
  configurations?: Configuration[];
  hasMetadata: boolean;
  childFolders?: Array<{
    id: number;
    name: string;
    createdDate: string;
    mask: number;
    owner: string;
    locked: boolean;
    parentId: number;
    aclClass: AclClass.folder;
    hasMetadata: boolean;
  }>;
  metadata?: Record<string, any>;
  data?: NgsData;
  storages?: Record<string, any>[];
};
