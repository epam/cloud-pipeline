import type {CloudRegion} from '../../../../../@types/regions.ts';
import type {
  FileShareMountListItem,
  ParsedFSMountPath,
  StoragePathFormValue,
  StoragePathInputState,
} from './types.ts';

const SERVER_PORT_MASK = /^[^:]+(:[\d]+)?$/i;

export function extractFileShareMountList(regions?: CloudRegion[]): FileShareMountListItem[] {
  const list: FileShareMountListItem[] = [];
  for (let i = 0; i < (regions || []).length; i++) {
    const region = regions![i];
    if (region.fileShareMounts?.length) {
      region.fileShareMounts.forEach((fileShareMount) => {
        if ((fileShareMount.mountRoot || '').trim()) {
          let separator = '';
          if (
            fileShareMount.mountType === 'NFS' &&
            SERVER_PORT_MASK.test(fileShareMount.mountRoot)
          ) {
            separator = ':';
          }
          const mountPathMask = new RegExp(`^${fileShareMount.mountRoot}${separator}(.*)$`, 'i');
          list.push({
            ...fileShareMount,
            mountPathMask,
            separator,
            regionName: region.name,
          });
        }
      });
    }
  }
  return list;
}

export function parseFSMountPath(
  pathInfo: StoragePathFormValue,
  fileShareMountsList: FileShareMountListItem[],
): ParsedFSMountPath {
  const [fileShareMountParseResult] = fileShareMountsList
    .map((fs) => {
      const execResult =
        +fs.id === +pathInfo.fileShareMountId! ? fs.mountPathMask.exec(pathInfo.path ?? '') : null;
      return {
        execResult,
        mount: fs,
      };
    })
    .filter((r) => !!r.execResult);

  if (fileShareMountParseResult) {
    return {
      fileShareMountId: fileShareMountParseResult.mount.id,
      regionId: fileShareMountParseResult.mount.regionId,
      storagePath: fileShareMountParseResult.execResult![1],
      path: pathInfo.path,
    };
  }

  return {...pathInfo};
}

export function storagePathChanged(
  oldValue: StoragePathFormValue | StoragePathInputState | undefined,
  newValue: StoragePathFormValue | StoragePathInputState | undefined,
): boolean {
  if (!!oldValue !== !!newValue) {
    return true;
  }
  if (!oldValue && !newValue) {
    return false;
  }
  return (
    oldValue!.fileShareMountId !== newValue!.fileShareMountId ||
    oldValue!.regionId !== newValue!.regionId ||
    oldValue!.path !== newValue!.path
  );
}
