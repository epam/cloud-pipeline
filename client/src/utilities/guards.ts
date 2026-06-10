import {
  Configuration,
  DataStorage,
  Folder,
  LibraryEntity,
  LibraryRootFolder,
  Pipeline,
} from '../@types/library.ts';

export function isLibraryRoot(
  root: LibraryEntity | LibraryRootFolder | undefined,
): root is LibraryRootFolder {
  return (
    !!root &&
    typeof root === 'object' &&
    (!Object.hasOwn(root, 'id') ||
      (root as {id?: unknown}).id === undefined ||
      (root as {id?: unknown}).id === null)
  );
}

export function isLibraryEntity(
  entity: LibraryEntity | LibraryRootFolder,
): entity is LibraryEntity {
  return (
    !!entity &&
    typeof entity === 'object' &&
    Object.hasOwn(entity, 'id') &&
    entity.id !== undefined &&
    entity.id !== null
  );
}

export function isFolder(item: LibraryEntity | LibraryRootFolder): item is Folder {
  return isLibraryEntity(item) && item.aclClass === 'FOLDER';
}

export function isPipeline(item: LibraryEntity | LibraryRootFolder): item is Pipeline {
  return isLibraryEntity(item) && item.aclClass === 'PIPELINE';
}

export function isDataStorage(item: LibraryEntity | LibraryRootFolder): item is DataStorage {
  return isLibraryEntity(item) && item.aclClass === 'DATA_STORAGE';
}

export function isConfiguration(item: LibraryEntity | LibraryRootFolder): item is Configuration {
  return isLibraryEntity(item) && item.aclClass === 'CONFIGURATION';
}

export function isNonNullable<T>(o: T | undefined | null): o is T {
  return o !== undefined && o !== null;
}
