import type { DataStorage } from '@cloud-pipeline/core';
import type { UIStorageItem } from '../storage-browser/types';

const NFS = 'NFS';
const OMICS_REF_BUCKET_TYPE = 'AWS_OMICS_REF';
const OMICS_SEQ_BUCKET_TYPE = 'AWS_OMICS_SEQ';

export function getItemFullPath(storage: DataStorage, item: UIStorageItem) {
  const type = storage.storageType ?? storage.type ?? '';
  const buildPath = (root: string) => (item && item.path ? `${root}/${item.path}` : root);
  if (type === NFS) {
    const storagePath = storage.path.replace(':', '');
    const mountPoint = storage.mountPoint
      ? storage.mountPoint.endsWith('/')
        ? storage.mountPoint.slice(0, -1)
        : storage.mountPoint
      : null;
    return buildPath(mountPoint ?? `/cloud-data/${storagePath}`);
  }
  if (type === OMICS_REF_BUCKET_TYPE || type === OMICS_SEQ_BUCKET_TYPE) {
    return buildPath(`${storage.pathMask}`);
  }
  return buildPath(`${type.toLowerCase()}://${storage.path}`);
}
