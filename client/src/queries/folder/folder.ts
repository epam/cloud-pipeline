import {queryOptions} from '@tanstack/react-query';
import {loadFolder, loadFolderTree} from '../../api/folder/folder-api.ts';
import {LibraryRootFolder} from '../../@types/library.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const folderKeys = {
  all: ['folder'] as const,
  details: () => [...folderKeys.all, 'detail'] as const,
  detail: (id: number) => [...folderKeys.details(), id] as const,
};

export const libraryTreeKeys = {
  all: ['library-tree'] as const,
};

export async function loadFolderWrapper(folderId: number | undefined) {
  if (!folderId) {
    return undefined;
  }
  return loadFolder(folderId);
}

export function folderQueryOptions(id: number | undefined, opts?: QueryOptionsParams) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  const queryKey = id !== undefined ? folderKeys.detail(id) : folderKeys.all;

  return queryOptions({
    ...queryOpts,
    queryKey,
    queryFn: () => loadFolderWrapper(id),
    enabled: enabled && id !== undefined,
  });
}

export function libraryTreeQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: libraryTreeKeys.all,
    queryFn: () => loadFolderTree(),
    placeholderData: {} as LibraryRootFolder,
  });
}

export function fetchFolder(id: number, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(folderQueryOptions(id, opts));
}

export function fetchLibraryTree(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(libraryTreeQueryOptions(opts));
}
