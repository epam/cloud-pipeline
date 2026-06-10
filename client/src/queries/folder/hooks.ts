import {useQuery} from '@tanstack/react-query';
import {useMemo} from 'react';
import {Folder, LibraryRootFolder} from '../../@types/library.ts';
import {findFolderItem} from '../../stores/folder/utilities.ts';
import {libraryTreeQueryOptions} from './folder.ts';
import {QueryOptionsParams} from '../types.ts';

export function useLibrarySubTree(
  options?: QueryOptionsParams & {
    parentFolderId?: number | undefined;
  },
): LibraryRootFolder | Folder | undefined {
  const {parentFolderId, ...cacheOptions} = options ?? {};
  const {data: tree = {} as LibraryRootFolder} = useQuery(libraryTreeQueryOptions(cacheOptions));
  return useMemo(
    () => (parentFolderId === undefined ? tree : findFolderItem(tree, parentFolderId)?.object),
    [parentFolderId, tree],
  );
}
