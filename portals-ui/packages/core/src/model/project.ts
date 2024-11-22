import { AclClass } from './acl';
import { Pipeline } from './pipeline';

export type ProjectsResponse = {
  childFolders: Project[];
  createdDate: string;
  mask: number;
  locked: boolean;
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

export type Project = {
  id: number;
  name: string;
  createdDate: string;
  mask: number;
  owner: string;
  locked: boolean;
  parentId?: number;
  pipelines?: Pipeline[];
  configurations?: Configuration[];
  aclClass: AclClass.folder;
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
  data: Record<string, any>;
  storages?: Record<string, any>[];
};
