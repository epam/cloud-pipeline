import type {CloudRegion, CloudRegionFileShareMount} from '../../../../../@types/regions.ts';

export type StoragePathFormValue = {
  fileShareMountId?: number;
  regionId?: number;
  path?: string;
};

export type StoragePathInputState = {
  fileShareMountId?: number | null;
  regionId?: number;
  path?: string;
  storagePath?: string;
};

export type FileShareMountListItem = CloudRegionFileShareMount & {
  mountPathMask: RegExp;
  separator: string;
  regionName: string;
};

export type ParsedFSMountPath = StoragePathFormValue & {
  storagePath?: string;
};

export interface DataStoragePathInputProps {
  value?: StoragePathFormValue;
  onChange?: (value: StoragePathFormValue) => void;
  disabled?: boolean;
  isFS?: boolean;
  isNew?: boolean;
  addExistingStorageFlag?: boolean;
  cloudRegions?: CloudRegion[];
  visible?: boolean;
  onPressEnter?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  onValidation?: (valid: boolean) => void;
}
