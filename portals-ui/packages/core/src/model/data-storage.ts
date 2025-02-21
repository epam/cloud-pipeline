import { AclClass, AclEntry } from './acl';
import { DataStorageItemTypes } from './enums';

export type DataStorage = AclEntry<AclClass.dataStorage> & {
  id: number;
  name: string;
  path: string;
  pathMask?: string;
  mountPoint?: string;
  type?: string;
  storageType?: string;
};

export type DataStoragePageResponse = {
  results: DataStorageItem[];
  nextPageMarker?: string;
};

export type DataStorageItem = {
  labels?: Record<string, string>;
  name: string;
  path: string;
  tags?: Record<string, string>;
  type: DataStorageItemTypes;
};
