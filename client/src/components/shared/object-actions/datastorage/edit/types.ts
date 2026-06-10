import type {StorageServiceType} from '../../../../../@types/datastorage.ts';

export type {StorageServiceType};

export const SERVICE_TYPES = {
  objectStorage: 'OBJECT_STORAGE' as StorageServiceType,
  fileShare: 'FILE_SHARE' as StorageServiceType,
  omicsRef: 'AWS_OMICS_REF' as StorageServiceType,
  omicsSeq: 'AWS_OMICS_SEQ' as StorageServiceType,
} as const;

export const OMICS_SERVICE_TYPE_LABELS: Record<string, string> = {
  AWS_OMICS_REF: 'Reference store',
  AWS_OMICS_SEQ: 'Sequence store',
};

export type PermissionsRestrictionRule = {
  role: string;
  readonly: boolean;
  onlyDefaultStorage: boolean;
  disabled: string[];
  enabledMask: number;
  defaultMask: number;
};

export type PermissionsRestrictions = {
  defaultMask: Array<{role: string; mask: number}>;
  enabledMask: Array<{role: string; mask: number}>;
  readOnlyRoles: string[];
};

export type StorageFormValues = {
  path: unknown;
  name?: string;
  description?: string;
  regionId?: string;
  backupDuration?: number;
  mountPoint?: string;
  mountOptions?: string;
  toolsToMount?: unknown;
  omicsType?: string;
  serviceType?: StorageServiceType;
  mountDisabled?: boolean;
  versioningEnabled?: boolean;
  backupDurationValue?: number;
  pathPermissionsEnabled?: boolean;
  sensitive?: boolean;
  skipPolicy?: boolean;
  shared?: boolean;
  fileShareMountId?: number;
};
