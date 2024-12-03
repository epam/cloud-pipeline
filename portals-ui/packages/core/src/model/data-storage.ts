import { AclClass, AclEntry } from './acl';

export type DataStorage = AclEntry<AclClass.dataStorage> & {
  id: number;
  name: string;
  path: string;
  pathMask?: string;
};
