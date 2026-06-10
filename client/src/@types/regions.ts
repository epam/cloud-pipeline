import {MaskedObject} from './common.ts';

export type CloudRegionFileShareMount = {
  id: number;
  regionId: number;
  mountRoot: string;
  mountType: string;
  mountOptions?: string;
};

export type MountFileStorageRule = 'NONE' | 'CLOUD' | 'REGION' | 'ALL';

export type CloudRegionProvider = 'AWS' | 'GCP' | 'AZURE' | 'LOCAL';

export type StorageLifecycleServiceProperties = {
  properties?: Record<string, unknown>;
};

export type ClusterStateRegionProperties = {
  tagsFilter?: Record<string, unknown>;
};

export type RunShiftPolicy = {
  shiftEnabled?: boolean;
};

export type CloudRegion = MaskedObject & {
  id: number;
  name: string;
  provider: CloudRegionProvider;
  regionId: string;
  default: boolean;
  globalDistributionUrl?: string;
  dnsHostedZoneId?: string;
  dnsHostedZoneBase?: string;
  fileShareMounts?: CloudRegionFileShareMount[];
  mountFileStorageRule?: MountFileStorageRule;
  mountCredentialsRule?: MountFileStorageRule;
  mountStorageRule: MountFileStorageRule;
  storageLifecycleServiceProperties?: StorageLifecycleServiceProperties;
  runShiftPolicy?: RunShiftPolicy;
  clusterInclude?: boolean;
  clusterStateRegionProperties?: ClusterStateRegionProperties;
  corsRules?: string;
  kmsKeyId?: string;
  kmsKeyArn?: string;
  sshKeyName?: string;
  tempCredentialsRole?: string;
  backupDuration?: number;
  versioningEnabled?: boolean;
};
