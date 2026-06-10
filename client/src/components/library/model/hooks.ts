import {Folder, LibraryEntity, LibraryRootFolder} from '../../../@types/library.ts';
import {LibraryItem, LibraryItemType} from '../types.ts';
import {useCallback, useEffect, useMemo, useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {useLocation, useNavigate} from 'react-router-dom';
import {routeingPaths} from '../../../routing/paths.ts';
import {
  assignMetadata,
  findLibraryItemPath,
  findLibraryItemPathByItemId,
  getLibraryItemIdFromPathname,
  getLibraryItemPathFromItemId,
  LibraryTreePlainListBuildOptions,
  LibraryTreePlainListPresentationOptions,
  makePlainList,
  makePresentationList,
  searchLibraryItemsAsync,
} from './tree.ts';
import {useMemoizedArray} from '../../../hooks/common/memo.ts';
import {
  metadataFolderQueryOptions,
  pipelineVersionsQueryOptions,
  useMetadataFolderEntities,
  usePipelineVersionsForAllPipelines,
} from '../../../queries';
import {isLibraryEntity, isLibraryRoot, isPipeline} from '../../../utilities/guards.ts';
import {useUiHiddenObjects} from '../../../stores/preferences/named-preferences/ui-hidden-objects.ts';
import {useIsAdministrator} from '../../../stores/users/hooks.ts';
import {MetadataLoadResponseItem} from '../../../@types/metadata.ts';

export function useLibraryTreePlainListBuildOptions(options: {
  includeRootFolder: boolean;
  includeBackItem?: boolean;
}): LibraryTreePlainListBuildOptions {
  const {includeRootFolder, includeBackItem} = options;
  const projectIds = useProjectIds();
  const pipelineVersions = usePipelineVersions();
  const {config: hiddenObjects} = useUiHiddenObjects();
  const isAdmin = useIsAdministrator();
  return useMemo<LibraryTreePlainListBuildOptions>(
    () => ({
      includeRootFolder,
      includeBackItem,
      projectIds,
      pipelineVersions,
      hiddenObjects,
      checkHiddenObjects: !isAdmin,
    }),
    [projectIds, pipelineVersions, hiddenObjects, isAdmin, includeRootFolder, includeBackItem],
  );
}

export function useLibraryTreePlainList(
  tree: LibraryRootFolder | Folder | undefined,
  options: Pick<LibraryTreePlainListBuildOptions, 'includeRootFolder' | 'includeBackItem'>,
): LibraryItem[] {
  const {includeRootFolder, includeBackItem} = options;
  const {
    projectIds: _projectIds,
    pipelineVersions: _pipelineVersions,
    hiddenObjects,
    checkHiddenObjects,
  } = useLibraryTreePlainListBuildOptions(options);
  const projectIds = useMemoizedArray(_projectIds);
  const pipelineVersions = useMemoizedArray(_pipelineVersions);
  return useMemo(
    () =>
      makePlainList(tree, {
        includeRootFolder,
        projectIds,
        pipelineVersions,
        hiddenObjects,
        checkHiddenObjects,
        includeBackItem,
      }),
    [
      tree,
      includeRootFolder,
      projectIds,
      pipelineVersions,
      hiddenObjects,
      checkHiddenObjects,
      includeBackItem,
    ],
  );
}

export function useLibraryTreePlainListPresentation(
  plainList: LibraryItem[],
  options: LibraryTreePlainListPresentationOptions,
): LibraryItem[] {
  const {expandedKeys: _expandedKeys, searchResultIds: _searchResultIds, searchMode} = options;
  const expandedKeys = useMemoizedArray(_expandedKeys);
  const searchResultIds = useMemoizedArray(_searchResultIds);
  return useMemo(
    () => makePresentationList(plainList, {expandedKeys, searchResultIds, searchMode}),
    [plainList, expandedKeys, searchResultIds, searchMode],
  );
}

export function useLibraryTreeAssignedMetadata(
  plainList: LibraryItem[],
  metadata?: MetadataLoadResponseItem[],
) {
  return useMemo(() => assignMetadata(plainList, metadata), [plainList, metadata]);
}

export function useProjectIds(): number[] {
  const folderEntities = useMetadataFolderEntities({
    entityClasses: ['FOLDER'],
    onlyLoaded: true,
  });
  const projects = useMemo(
    () =>
      folderEntities
        .filter(
          (m) =>
            m.data &&
            Object.hasOwn(m.data, 'type') &&
            typeof m.data['type']?.value === 'string' &&
            m.data['type'].value.toLowerCase() === 'project',
        )
        .map((o) => o.entity?.entityId)
        .filter((o) => o !== undefined),
    [folderEntities],
  );
  return useMemoizedArray(projects);
}

export function usePipelineVersions() {
  return usePipelineVersionsForAllPipelines();
}

export function useLibraryItemPath(
  plainList: LibraryItem[],
  type: LibraryItemType,
  identifier: number,
  sub?: string,
): LibraryItem[] {
  return useMemo(
    () => findLibraryItemPath(plainList, type, identifier, sub),
    [plainList, type, identifier, sub],
  );
}

export function useLibraryItemPathByItemId(
  plainList: LibraryItem[],
  itemId?: string,
): LibraryItem[] {
  return useMemo(() => findLibraryItemPathByItemId(plainList, itemId), [plainList, itemId]);
}

export function useRoutedLibraryItem(): [itemId: string | undefined, (itemId: string) => void] {
  const {pathname} = useLocation();
  const navigate = useNavigate();
  const itemId = useMemo(() => getLibraryItemIdFromPathname(pathname), [pathname]);
  const setItemId = useCallback(
    (newItemId: string) => {
      if (!newItemId) {
        navigate(routeingPaths.library);
        return;
      }
      const path = getLibraryItemPathFromItemId(newItemId);
      if (path) {
        navigate(path);
      }
    },
    [navigate],
  );
  return useMemo(() => [itemId, setItemId], [itemId, setItemId]);
}

function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  );
}

export function useLibraryTreeSearchResults(
  items: LibraryItem[],
  search: string,
): {results: LibraryItem[] | undefined; pending: boolean; searchMode: boolean} {
  const [results, setResults] = useState<LibraryItem[] | undefined>();
  const [pending, setPending] = useState(false);

  useEffect(() => {
    const trimmed = search.trim();
    if (trimmed.length === 0) {
      setResults(undefined);
      setPending(false);
      return undefined;
    }

    const abortController = new AbortController();
    const {signal} = abortController;

    setPending(true);

    searchLibraryItemsAsync(items, trimmed, {
      signal,
      searchResultsCallback: async (results, _, done) => {
        setResults(results.slice());
        setPending(!done);
      },
    }).catch((error) => {
      if (!signal.aborted && !isAbortError(error)) {
        console.warn(error);
      }
    });

    return () => {
      abortController.abort();
    };
  }, [items, search]);
  const searchMode = search.trim().length > 0;
  return useMemo(() => ({results, pending, searchMode}), [results, pending, searchMode]);
}

export function useLibraryItemLoader(): (item: LibraryRootFolder | LibraryEntity) => Promise<void> {
  const queryClient = useQueryClient();
  return useCallback(
    async (item: LibraryEntity | LibraryRootFolder) => {
      if (isLibraryEntity(item)) {
        switch (item.aclClass) {
          case 'FOLDER':
            await queryClient.fetchQuery(metadataFolderQueryOptions(item.id));
            break;
          case 'PIPELINE':
            if (isPipeline(item) && item.pipelineType !== 'VERSIONED_STORAGE') {
              await queryClient.fetchQuery(pipelineVersionsQueryOptions(item.id));
            }
            break;
          default:
            break;
        }
        return;
      }
      await queryClient.fetchQuery(metadataFolderQueryOptions(undefined));
    },
    [queryClient],
  );
}
