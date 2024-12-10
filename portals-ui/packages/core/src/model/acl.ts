export enum AclClass {
  user = 'USER',
  role = 'ROLE',
  pipeline = 'PIPELINE',
  folder = 'FOLDER',
  dataStorage = 'DATA_STORAGE',
}

export type AclEntry<Class extends AclClass = AclClass> = {
  aclClass: Class;
  mask: number;
  owner: string;
};

type NgsData = {
  type?: string;
  value: string;
};

export type NgsTags = Record<string, NgsData>;

export enum PermissionsScope {
  read = 0,
  write = 1,
  execute = 2,
  owner = 3,
}

export enum Permission {
  inherit = 0,
  allow = 1,
  deny = 2,
}
