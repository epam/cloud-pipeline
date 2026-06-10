import type {DataStorageType, DataStorage, StoragePolicy} from './library.ts';
import type {MaskedObject} from './common.ts';

export type DataStorageItemType = 'File' | 'Folder';

export type UpdateDataStorageItemActionType = 'Create' | 'Move';

export type StorageServiceType =
  | 'FILE_SHARE'
  | 'OBJECT_STORAGE'
  | 'AWS_OMICS_REF'
  | 'AWS_OMICS_SEQ';

export type DataStorageItemBase = {
  name?: string;
  path?: string;
  labels?: Record<string, string>;
  tags?: Record<string, string>;
  type?: DataStorageItemType;
};

export type DataStorageFolder = DataStorageItemBase & {
  type: 'Folder';
};

export type DataStorageFile = DataStorageItemBase & {
  type: 'File';
  size?: number;
  changed?: string;
  version?: string;
  deleteMarker?: boolean;
  isHidden?: boolean;
  latest?: boolean;
};

export type DataStorageItem = DataStorageFile | DataStorageFolder;

export type DataStorageListItem = DataStorageItem & Partial<MaskedObject>;

export type DataStorageListing = {
  nextPageMarker?: string;
  parentFolderMask?: number;
  results?: DataStorageItem[];
};

export type DataStorageListingFilter = {
  dateBefore?: string;
  dateAfter?: string;
  sizeGreaterThan?: number;
  sizeLessThan?: number;
  nameFilter?: string;
};

export type UpdateDataStorageItem = {
  path?: string;
  oldPath?: string;
  version?: string;
  type?: DataStorageItemType;
  action?: UpdateDataStorageItemActionType;
  contents?: string;
};

export type DataStorageItemContent = {
  content?: string;
  contentType?: string;
  truncated?: boolean;
  mayBeBinary?: boolean;
};

export type DataStorageDownloadFileUrl = {
  url?: string;
  expires?: string;
  tagValue?: string;
};

export type GenerateDownloadUrlRequest = {
  paths?: string[];
  permissions?: string[];
  hours?: number;
};

export type DataStorageVO = {
  id?: number;
  name?: string;
  description?: string;
  path?: string;
  serviceType?: StorageServiceType;
  type?: DataStorageType;
  createdDate?: string;
  parentFolderId?: number;
  storagePolicy?: StoragePolicy;
  mountOptions?: string;
  mountPoint?: string;
  shared?: boolean;
  allowedCidrs?: string[];
  regionId?: number;
  fileShareMountId?: number;
  mountExactPath?: boolean;
  sensitive?: boolean;
  toolsToMount?: unknown;
  mountDisabled?: boolean;
  useAssumedCredentials?: boolean;
  pathPermissionsEnabled?: boolean;
};

export type DataStorageTagSearchResult = {
  datastorageId: number;
  items: Array<{
    object: {path: string; version?: string};
    key: string;
    value: string;
    createdDate?: string;
  }>;
};

export type {DataStorage, StoragePolicy, DataStorageType};
