import type {ReactNode} from 'react';
import type {Permission, PermissionSid} from '../../../api/users/grants-api.ts';

export type {Permission, PermissionSid};

export const PERMISSION_COLUMNS = {
  allow: 'allow',
  deny: 'deny',
} as const;

export type PermissionColumn = (typeof PERMISSION_COLUMNS)[keyof typeof PERMISSION_COLUMNS];

export const PERMISSIONS = {
  read: 'read',
  write: 'qwrite',
  execute: 'execute',
} as const;

export type PermissionType = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

export type MaskRule = {
  mask: number;
  role: string;
};

export type SubObjectToCheck = {
  entityId: number | string;
  entityClass: string;
  name?: ReactNode;
  description?: ReactNode;
};

export type SubObjectPermissions = {
  object: SubObjectToCheck;
  permissions: Permission[];
  owner?: string;
};

export interface PermissionsFormProps {
  objectType?: string;
  objectIdentifier?: string | number;
  readonly?: boolean;
  defaultMask?: number | MaskRule[];
  enabledMask?: number | MaskRule[];
  readOnlyRoles?: string[];
  subObjectsPermissionsMaskToCheck?: number;
  subObjectsToCheck?: SubObjectToCheck[];
  subObjectsPermissionsErrorTitle?: ReactNode;
  showOwner?: boolean;
  editOwnerAvailable?: boolean;
  refreshPermissionsAfterUpdate?: boolean;
  permissionsColumns?: PermissionColumn[];
  availablePermissions?: PermissionType[];
}
