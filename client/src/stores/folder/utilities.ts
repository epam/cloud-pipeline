import {Folder, LibraryEntity, LibraryRootFolder} from '../../@types/library.ts';
import {loadFolder} from '../../api';

export async function loadFolderWrapper(folderId: number | undefined): Promise<Folder | undefined> {
  if (!folderId) {
    return undefined;
  }
  return loadFolder(folderId);
}

export type FindResult<T extends LibraryEntity | LibraryRootFolder> = {
  object: T;
  parent?: Folder | LibraryRootFolder;
  parents: Array<Folder | LibraryRootFolder>;
};

export function findFolderItem(
  tree: LibraryRootFolder | Folder,
  folderId: number,
): FindResult<Folder> | undefined {
  if (!tree.childFolders) {
    return undefined;
  }
  for (const child of tree.childFolders) {
    const result = findFolderItem(child, folderId);
    if (result) {
      return {
        ...result,
        parents: [tree].concat(result.parents),
      };
    }
  }
  return undefined;
}
