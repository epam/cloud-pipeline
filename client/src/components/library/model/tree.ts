import {createElement, ReactNode} from 'react';
import {Folder, LibraryEntity, LibraryRootFolder, Pipeline} from '../../../@types/library.ts';
import {LibraryItem, LibraryItemType} from '../types.ts';
import {isDataStorage, isFolder, isLibraryRoot, isPipeline} from '../../../utilities/guards.ts';
import {PipelineVersionsInfo} from '../../../stores/pipelines/types.ts';
import {HiddenObjectsConfig} from '../../../stores/preferences/named-preferences/ui-hidden-objects.ts';
import {routeingPaths} from '../../../routing/paths.ts';
import {CloudRegionTag} from '../../shared/cloud-region-tag/cloud-region-tag.tsx';
import {escapeRegExp} from '../../../utilities/misc.ts';
import {
  MetadataEntityData,
  MetadataEntityRef,
  MetadataLoadResponseItem,
} from '../../../@types/metadata.ts';

export type LibraryTreePlainListBuildOptions = {
  includeRootFolder: boolean;
  includeBackItem?: boolean;
  projectIds: number[];
  pipelineVersions: PipelineVersionsInfo[];
  hiddenObjects: HiddenObjectsConfig;
  checkHiddenObjects: boolean;
};
export type LibraryTreePlainListPresentationOptions = {
  expandedKeys: string[];
  searchResultIds: string[];
  searchMode: boolean;
};

function sortLibraryEntities(a: LibraryEntity, b: LibraryEntity): number {
  if (isPipeline(a) && isPipeline(b) && a.pipelineType !== b.pipelineType) {
    if (a.pipelineType === 'VERSIONED_STORAGE') {
      return -1;
    }
    return 1;
  }
  return a.name.localeCompare(b.name);
}

function makePlainListRecursively(
  root: Folder | LibraryRootFolder,
  options: LibraryTreePlainListBuildOptions & {
    level: number;
    parentIndex: number;
    currentIndex: number;
  },
): LibraryItem[] {
  const {
    level,
    includeRootFolder = true,
    parentIndex,
    currentIndex: _currentIndex,
    projectIds,
    pipelineVersions,
    hiddenObjects,
    checkHiddenObjects,
    includeBackItem,
  } = options;
  if (isFolder(root) && checkHiddenObjects && hiddenObjects.folderIsHidden(root.id)) {
    return [];
  }
  let currentIndex = _currentIndex;
  const {
    childFolders: _childFolders = [],
    configurations: _configurations = [],
    storages: _storages = [],
    pipelines: _pipelines = [],
  } = root;
  const childFolders = _childFolders
    .slice()
    .sort(sortLibraryEntities)
    .filter((f) => !checkHiddenObjects || !hiddenObjects.folderIsHidden(f.id));
  const configurations = _configurations
    .slice()
    .sort(sortLibraryEntities)
    .filter((c) => !checkHiddenObjects || !hiddenObjects.configurationIsHidden(c.id));
  const storages = _storages
    .slice()
    .sort(sortLibraryEntities)
    .filter((s) => !checkHiddenObjects || !hiddenObjects.dataStorageIsHidden(s.id));
  const pipelines = _pipelines
    .slice()
    .sort(sortLibraryEntities)
    .filter((p) => !checkHiddenObjects || !hiddenObjects.pipelineIsHidden(p.id));
  let result: LibraryItem[] = [];
  const rootObj = makeLibraryTreeBaseItem(root, {level, parentIndex, projectIds});
  if (isLibraryRoot(root)) {
    result.push(
      makeLibraryTreeCollectionItem(root, LibraryItemType.pipelines, {level, parentIndex}),
    );
    result.push(
      makeLibraryTreeCollectionItem(root, LibraryItemType.storages, {level, parentIndex}),
    );
    currentIndex += 2;
  }
  if (isFolder(root) && includeBackItem) {
    result.push({
      ...makeLibraryTreeBaseItem(
        root.parentId
          ? {
              aclClass: 'FOLDER',
              id: root.parentId,
              mask: 1,
              name: '...',
              createdDate: '',
              owner: '',
            }
          : {},
        {level, parentIndex, projectIds},
      ),
      type: LibraryItemType.back,
      name: '...',
    });
    currentIndex += 1;
  }
  if (includeRootFolder) {
    currentIndex += 1;
    result.push(rootObj);
  }
  const childOptions = {
    include: true,
    level: level + (includeRootFolder ? 1 : 0),
    parentIndex: currentIndex,
    includeRootFolder: true,
    projectIds,
    currentIndex,
    pipelineVersions,
    hiddenObjects,
    checkHiddenObjects,
  };
  const addChildren = (children: LibraryItem[]) => {
    result = result.concat(children);
    childOptions.currentIndex += children.length;
  };
  childFolders.forEach((child) => {
    addChildren(makePlainListRecursively(child, childOptions));
  });
  storages.forEach((storage) => {
    addChildren([makeLibraryTreeBaseItem(storage, childOptions)]);
  });
  pipelines.forEach((pipeline) => {
    addChildren(makeLibraryTreePipelineItems(pipeline, childOptions));
  });
  configurations.forEach((configuration) => {
    addChildren([makeLibraryTreeBaseItem(configuration, childOptions)]);
  });
  const mc = isFolder(root) ? makeLibraryTreeMetadataItems(root, childOptions) : undefined;
  if (mc) {
    const {container, classes} = mc;
    addChildren([container].concat(classes));
  }
  if (isFolder(root) && projectIds.includes(root.id)) {
    addChildren([makeLibraryTreeProjectHistoryItem(root, childOptions)]);
  }
  return result;
}

export function makePlainList(
  root: LibraryRootFolder | Folder | undefined,
  options: LibraryTreePlainListBuildOptions,
): LibraryItem[] {
  if (!root) {
    return [];
  }
  return makePlainListRecursively(root, {
    ...options,
    parentIndex: -1,
    currentIndex: -1,
    level: 0,
  });
}

export function makePresentationList(
  plain: LibraryItem[],
  options: LibraryTreePlainListPresentationOptions,
): LibraryItem[] {
  const {expandedKeys, searchResultIds = [], searchMode} = options;
  const expandedKeysSet = new Set(expandedKeys);
  const result = plain.map((o) =>
    searchMode ? {...o, expanded: false, visible: false} : {...o, expanded: false, visible: true},
  );
  if (searchMode) {
    const revertOpen = (indices: Set<number>, level: number) => {
      const parents = new Set<number>();
      for (const index of indices) {
        result[index].visible = true;
        result[index].searchHit = level === 0;
        if (
          level > 0 &&
          result[index].expandable &&
          result[index].type !== LibraryItemType.pipeline
        ) {
          result[index].expanded = true;
        }
        if (result[index].parentIndex >= 0) {
          parents.add(result[index].parentIndex);
        }
      }
      return parents;
    };
    let set = new Set(
      searchResultIds.map((id) => plain.findIndex((v) => v.id === id)).filter((id) => id >= 0),
    );
    let iteration = 0;
    while (set.size > 0) {
      set = revertOpen(set, iteration);
      iteration += 1;
    }
    // now, in search mode we need to display all children for expanded items
    for (const item of result) {
      const parent = item.parentIndex >= 0 ? result[item.parentIndex] : undefined;
      if (parent && expandedKeysSet.has(parent.id)) {
        item.visible = true;
      }
      if (expandedKeysSet.has(item.id)) {
        item.expanded = true;
      }
    }
    return result;
  }
  for (let i = 0; i < result.length; i += 1) {
    const item = result[i];
    const parent = item.parentIndex >= 0 ? result[item.parentIndex] : undefined;
    item.visible = !parent || (parent.expanded && parent.visible);
    if (item.visible && expandedKeysSet.size > 0) {
      item.expanded = expandedKeysSet.has(item.id);
    }
  }
  return result;
}

export function assignMetadata(
  list: LibraryItem[],
  metadata?: MetadataLoadResponseItem[],
): LibraryItem[] {
  const findMetadata = (item: LibraryItem) =>
    item.object &&
    [
      LibraryItemType.folder,
      LibraryItemType.project,
      LibraryItemType.storage,
      LibraryItemType.pipeline,
      LibraryItemType.configuration,
    ].includes(item.type)
      ? (metadata?.filter(
          (o) =>
            o.entity &&
            o.entity.entityId === item.object.id &&
            o.entity.entityClass === item.object.aclClass,
        ) ?? [])
      : [];
  const mapMetadata = (item: LibraryItem): LibraryItem => {
    const meta = findMetadata(item);
    return {
      ...item,
      issuesCount: meta.reduce((acc, cur) => acc + (cur.issuesCount ?? 0), 0),
      metadata: meta.reduce<MetadataEntityData>(
        (acc, cur) => ({
          ...acc,
          ...(cur.data ?? {}),
        }),
        {},
      ),
    };
  };
  return list.map(mapMetadata);
}

export const LIBRARY_ROOT_ID = 'library-root';

const LIBRARY_ROUTE_PREFIXES = new Set([
  'library',
  'pipelines',
  'storages',
  'folder',
  'storage',
  'configuration',
  'vs',
]);

export function getLibraryFolderTreeItemId(item: Folder | LibraryRootFolder) {
  if (isFolder(item)) {
    return `${LibraryItemType.folder}|${item.id}`;
  }
  return LIBRARY_ROOT_ID;
}

function getLibraryTreeItemId(type: LibraryItemType, identifier: number, sub?: number | string) {
  if (type === LibraryItemType.library) {
    return LIBRARY_ROOT_ID;
  }
  if (sub === undefined) {
    return `${type}|${identifier}`;
  }
  return `${type}|${identifier}|${sub}`;
}

export function parseLibraryItemId(itemId: string | undefined):
  | {
      type: LibraryItemType;
      identifier: number;
      sub?: string;
    }
  | undefined {
  if (!itemId) {
    return undefined;
  }
  if (itemId === LIBRARY_ROOT_ID) {
    return {type: LibraryItemType.library, identifier: 0};
  }
  const [type, identifier, sub] = itemId.split('|');
  if (!type || identifier === undefined || Number.isNaN(Number(identifier))) {
    return undefined;
  }
  return {type: type as LibraryItemType, identifier: Number(identifier), sub};
}

export function getMetadataEntityRefFromLibraryItemId(
  itemId: string | undefined,
): MetadataEntityRef | undefined {
  const parsed = parseLibraryItemId(itemId);
  if (!parsed) {
    return undefined;
  }
  switch (parsed.type) {
    case LibraryItemType.folder:
    case LibraryItemType.project:
      return {entityId: parsed.identifier, entityClass: 'FOLDER'};
    case LibraryItemType.storage:
      return {entityId: parsed.identifier, entityClass: 'DATA_STORAGE'};
    case LibraryItemType.pipeline:
      return {entityId: parsed.identifier, entityClass: 'PIPELINE'};
    default:
      return undefined;
  }
}

export function getLibraryItemIdFromPathname(pathname: string): string | undefined {
  const parts = pathname.split('/').filter(Boolean);
  const [p0, p1, p2, p3] = parts;
  if (!p0) {
    return undefined;
  }

  const p0Lower = p0.toLowerCase();

  if (LIBRARY_ROUTE_PREFIXES.has(p0Lower)) {
    switch (p0Lower) {
      case 'library':
        return LIBRARY_ROOT_ID;
      case 'pipelines':
        return getLibraryTreeItemId(LibraryItemType.pipelines, 0);
      case 'storages':
        return getLibraryTreeItemId(LibraryItemType.storages, 0);
      case 'folder': {
        if (!p1) {
          return LIBRARY_ROOT_ID;
        }
        if (p2?.toLowerCase() === 'history') {
          return getLibraryTreeItemId(LibraryItemType.projectHistory, Number(p1));
        }
        if (p2?.toLowerCase() === 'metadata') {
          if (p3) {
            return getLibraryTreeItemId(LibraryItemType.metadataClass, Number(p1), p3);
          }
          return getLibraryTreeItemId(LibraryItemType.metadata, Number(p1));
        }
        return getLibraryTreeItemId(LibraryItemType.folder, Number(p1));
      }
      case 'storage':
        return p1 ? getLibraryTreeItemId(LibraryItemType.storage, Number(p1)) : '';
      case 'configuration':
        return p1 ? getLibraryTreeItemId(LibraryItemType.configuration, Number(p1)) : '';
      case 'vs':
        return p1 ? getLibraryTreeItemId(LibraryItemType.pipeline, Number(p1)) : '';
      default:
        return '';
    }
  }

  if (p1 === 'refs' && p2?.toLowerCase() === 'heads') {
    const pipelineId = Number(p0);
    if (!Number.isNaN(pipelineId)) {
      return getLibraryTreeItemId(LibraryItemType.pipeline, pipelineId);
    }
    return '';
  }

  if (!Number.isNaN(Number(p0))) {
    const pipelineId = Number(p0);
    if (!p1) {
      return getLibraryTreeItemId(LibraryItemType.pipeline, pipelineId);
    }
    return getLibraryTreeItemId(LibraryItemType.pipelineVersion, pipelineId, p1);
  }

  return undefined;
}

export function getLibraryItemPathFromItemId(itemId: string): string | undefined {
  if (!itemId) {
    return undefined;
  }
  if (itemId === LIBRARY_ROOT_ID) {
    return routeingPaths.library;
  }
  const parsed = parseLibraryItemId(itemId);
  if (!parsed) {
    return undefined;
  }
  const {type, identifier, sub} = parsed;
  if (type === LibraryItemType.library) {
    return routeingPaths.library;
  }
  if (type === LibraryItemType.pipelineVersion && sub) {
    return getLibraryTreeItemUrl(type, identifier, sub);
  }
  return getLibraryTreeItemUrl(type, identifier, sub);
}

export function findLibraryItemPathByItemId(plain: LibraryItem[], itemId?: string): LibraryItem[] {
  if (!itemId) {
    return [];
  }
  const parsed = parseLibraryItemId(itemId);
  if (parsed) {
    return findLibraryItemPath(plain, parsed.type, parsed.identifier, parsed.sub);
  }
  return [];
}

export function findLibraryItemPath(
  plain: LibraryItem[],
  type: LibraryItemType,
  identifier: number,
  sub?: number | string,
): LibraryItem[] {
  let itemId = getLibraryTreeItemId(type, identifier, sub);
  let itemIndex = plain.findIndex((o) => o.id === itemId);
  if (itemIndex === -1) {
    switch (type) {
      case LibraryItemType.pipelineVersion:
        // pipeline version was not loaded yet
        itemId = getLibraryTreeItemId(LibraryItemType.pipeline, identifier);
        itemIndex = plain.findIndex((o) => o.id === itemId);
        break;
      case LibraryItemType.projectHistory:
        // parent folder metadata was not loaded yet
        itemId = getLibraryTreeItemId(LibraryItemType.folder, identifier);
        itemIndex = plain.findIndex((o) => o.id === itemId);
        break;
      default:
        break;
    }
  }
  const result: LibraryItem[] = [];
  while (itemIndex >= 0) {
    result.push(plain[itemIndex]);
    itemIndex = plain[itemIndex].parentIndex;
  }
  return result;
}

export function libraryItemMatchesSearchCriteria(
  item: LibraryItem,
  search: string | RegExp,
): boolean {
  if (typeof search === 'string') {
    if (search === '') {
      return true;
    }
    return item.searchableParts.some((part) => part.toLowerCase().indexOf(search.toLowerCase()));
  }
  return item.searchableParts.some((part) => search.test(part));
}

type SearchOptions = {
  advanced?: boolean;
};

type AsyncSearchOptions = SearchOptions & {
  searchResultsCallback?: (
    results: LibraryItem[],
    batch: LibraryItem[],
    done: boolean,
  ) => void | Promise<void>;
  batchSize?: number;
  signal?: AbortSignal;
};

function yieldToMain(): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, 0);
  });
}

function throwIfAborted(signal?: AbortSignal): void {
  if (signal?.aborted) {
    throw signal.reason ?? new DOMException('Aborted', 'AbortError');
  }
}

function buildSearchRegExp(search: string | RegExp, options?: SearchOptions): RegExp | undefined {
  const {advanced = true} = options ?? {};
  if (typeof search === 'string' && search.trim() === '') {
    return undefined;
  }
  let searchRegExp =
    typeof search === 'string' ? new RegExp(escapeRegExp(search.trim()), 'i') : search;
  if (advanced && typeof search === 'string') {
    const trimmed = search.trim();
    let r = '';
    for (let i = 0; i < trimmed.length; i += 1) {
      r = r.concat(escapeRegExp(trimmed[i])).concat('.*');
    }
    searchRegExp = new RegExp(r, 'i');
  }
  return searchRegExp;
}

export function searchLibraryItems(
  items: LibraryItem[],
  search: string | RegExp,
  options?: SearchOptions,
): LibraryItem[] {
  const searchRegExp = buildSearchRegExp(search, options);
  if (!searchRegExp) {
    return items;
  }
  return items.filter((item) => libraryItemMatchesSearchCriteria(item, searchRegExp));
}

export async function searchLibraryItemsAsync(
  items: LibraryItem[],
  search: string | RegExp,
  options?: AsyncSearchOptions,
): Promise<LibraryItem[]> {
  const searchRegExp = buildSearchRegExp(search, options);
  if (!searchRegExp) {
    return items;
  }
  const {searchResultsCallback, batchSize = 5000, signal} = options ?? {};
  let results: LibraryItem[] = [];

  for (let i = 0; i < items.length; i += batchSize) {
    throwIfAborted(signal);

    const end = Math.min(i + batchSize, items.length);
    const batchResult = searchLibraryItems(items.slice(i, end), searchRegExp);
    results = results.concat(batchResult);

    const done = end >= items.length;

    if (searchResultsCallback) {
      await searchResultsCallback(results, batchResult, done);
    }

    throwIfAborted(signal);

    if (!done) {
      await yieldToMain();
    }
  }

  return results;
}

export function getLibraryTreeItemUrl(
  type: LibraryItemType,
  identifier: number,
  sub?: number | string,
) {
  switch (type) {
    case LibraryItemType.library:
      return routeingPaths.library;
    case LibraryItemType.folder:
      return routeingPaths.folder(identifier);
    case LibraryItemType.configuration:
      return routeingPaths.configuration(identifier);
    case LibraryItemType.metadata:
      return routeingPaths.folderMetadata(identifier);
    case LibraryItemType.metadataClass:
      return sub ? routeingPaths.folderMetadataClass(identifier, sub.toString()) : undefined;
    case LibraryItemType.pipeline:
      return routeingPaths.pipeline(identifier);
    case LibraryItemType.pipelineVersion:
      return sub ? routeingPaths.pipelineVersion(identifier, sub.toString()) : undefined;
    case LibraryItemType.storage:
      return routeingPaths.storage(identifier);
    case LibraryItemType.projectHistory:
      return routeingPaths.folderHistory(identifier);
    case LibraryItemType.storages:
      return identifier ? undefined : routeingPaths.storages; // root folder id - if it is not 0, do not link to the "all" storages
    case LibraryItemType.pipelines:
      return identifier ? undefined : routeingPaths.pipelines; // root folder id - if it is not 0, do not link to the "all" pipelines
    default:
      return undefined;
  }
}

export function getLibraryTreeItemType(item: LibraryEntity): LibraryItemType {
  switch (item.aclClass) {
    case 'CONFIGURATION':
      return LibraryItemType.configuration;
    case 'DATA_STORAGE':
      return LibraryItemType.storage;
    case 'FOLDER':
      return LibraryItemType.folder;
    case 'PIPELINE':
      return LibraryItemType.pipeline;
    default:
      return LibraryItemType.folder;
  }
}

const expandableTypes = new Set([
  LibraryItemType.pipeline,
  LibraryItemType.folder,
  LibraryItemType.metadata,
]);

function makeLibraryTreeBaseItem(
  item: LibraryEntity | LibraryRootFolder,
  options?: {level?: number; parentIndex: number; projectIds: number[]},
): LibraryItem {
  const {level = 0, parentIndex = -1, projectIds = []} = options ?? {};
  if (isLibraryRoot(item)) {
    return {
      id: getLibraryFolderTreeItemId(item),
      type: LibraryItemType.library,
      name: 'Library',
      object: item,
      parentIndex,
      level,
      expanded: false,
      visible: true,
      expandable: true,
      pending: false,
      url: routeingPaths.library,
      interactive: true,
      searchableParts: ['library'],
      searchHit: false,
      metadata: {},
      issuesCount: 0,
    };
  }
  const type = getLibraryTreeItemType(item);
  let details: ReactNode | undefined;
  if (isDataStorage(item)) {
    details = createElement(CloudRegionTag, {
      regionId: item.regionId,
      displayName: false,
      displayProvider: true,
    });
  }
  return {
    id: getLibraryTreeItemId(type, item.id),
    type: projectIds.includes(item.id) ? LibraryItemType.project : type,
    name: item.name,
    details,
    object: item,
    parentIndex,
    level,
    expanded: false,
    visible: true,
    expandable:
      expandableTypes.has(type) && (!isPipeline(item) || item.pipelineType !== 'VERSIONED_STORAGE'),
    pending: false,
    url: getLibraryTreeItemUrl(type, item.id),
    interactive: true,
    searchableParts: [item.name, item.description].filter(Boolean) as string[],
    searchHit: false,
    metadata: {},
    issuesCount: 0,
  };
}

function makeLibraryTreeCollectionItem(
  root: LibraryRootFolder | Folder,
  type: LibraryItemType.pipelines | LibraryItemType.storages,
  options?: {level?: number; parentIndex: number},
): LibraryItem {
  const {level = 0, parentIndex = -1} = options ?? {};
  let name = '';
  switch (type) {
    case LibraryItemType.pipelines:
      name = 'All pipelines';
      break;
    case LibraryItemType.storages:
      name = 'All storages';
      break;
    default:
      break;
  }
  return {
    id: getLibraryTreeItemId(type, 0),
    type,
    name,
    object: root,
    parentIndex,
    level,
    expanded: false,
    visible: true,
    expandable: false,
    pending: false,
    url: getLibraryTreeItemUrl(type, 0),
    interactive: true,
    searchableParts: [],
    searchHit: false,
    metadata: {},
    issuesCount: 0,
  };
}

function makeLibraryTreeProjectHistoryItem(
  item: Folder,
  options?: {level?: number; parentIndex: number},
): LibraryItem {
  const {level = 0, parentIndex = -1} = options ?? {};
  return {
    id: getLibraryTreeItemId(LibraryItemType.projectHistory, item.id),
    type: LibraryItemType.projectHistory,
    name: 'History',
    object: item,
    parentIndex,
    level,
    expanded: false,
    visible: true,
    expandable: false,
    pending: false,
    url: getLibraryTreeItemUrl(LibraryItemType.projectHistory, item.id),
    interactive: true,
    searchableParts: [],
    searchHit: false,
    metadata: {},
    issuesCount: 0,
  };
}

function makeLibraryTreePipelineItems(
  item: Pipeline,
  options?: {
    level?: number;
    parentIndex: number;
    currentIndex: number;
    pipelineVersions: PipelineVersionsInfo[];
    hiddenObjects: HiddenObjectsConfig;
    checkHiddenObjects: boolean;
  },
): LibraryItem[] {
  const {
    level = 0,
    parentIndex = -1,
    currentIndex = -1,
    pipelineVersions = [],
    checkHiddenObjects,
    hiddenObjects,
  } = options ?? {};
  if (checkHiddenObjects && hiddenObjects && hiddenObjects.pipelineIsHidden(item.id)) {
    return [];
  }
  const versions = pipelineVersions.find((info) => info.pipelineId === item.id);
  const id = getLibraryTreeItemId(LibraryItemType.pipeline, item.id);
  const pipeline: LibraryItem = {
    id,
    type: LibraryItemType.pipeline,
    name: item.name,
    object: item,
    parentIndex,
    level,
    expanded: false,
    visible: true,
    expandable: item.pipelineType !== 'VERSIONED_STORAGE',
    pending: versions?.pending ?? false,
    url: getLibraryTreeItemUrl(LibraryItemType.pipeline, item.id),
    interactive: true,
    searchableParts: [item.name, item.description].filter(Boolean) as string[],
    searchHit: false,
    metadata: {},
    issuesCount: 0,
  };
  let result: LibraryItem[] = [pipeline];
  if (item.pipelineType !== 'VERSIONED_STORAGE' && versions) {
    const pipelineIndex = currentIndex + 1;
    if (versions.versions.length === 0 && versions.pending) {
      result = result.concat([
        {
          id: getLibraryTreeItemId(LibraryItemType.pipelineVersion, item.id, 'loading'),
          type: LibraryItemType.loading,
          name: createElement('span', {className: 'text-xs'}, 'loading...'),
          object: item,
          parentIndex: pipelineIndex,
          level: level + 1,
          expanded: false,
          visible: true,
          expandable: false,
          pending: true,
          url: undefined,
          interactive: false,
          searchableParts: [],
          searchHit: false,
          metadata: {},
          issuesCount: 0,
        },
      ]);
    }
    result = result.concat(
      versions.versions
        .filter(
          (v) =>
            !checkHiddenObjects ||
            !hiddenObjects ||
            !(
              hiddenObjects.pipelineVersionIsHidden(item.id, v.name) ||
              hiddenObjects.pipelineVersionIsHidden(item.id, v.commitId)
            ),
        )
        .map((v) => ({
          id: getLibraryTreeItemId(LibraryItemType.pipelineVersion, item.id, v.name),
          type: LibraryItemType.pipelineVersion,
          name: v.name,
          object: item,
          revision: v,
          parentIndex: pipelineIndex,
          level: level + 1,
          expanded: false,
          visible: true,
          expandable: false,
          pending: false,
          url: getLibraryTreeItemUrl(LibraryItemType.pipelineVersion, item.id, v.name),
          interactive: true,
          searchableParts: [v.name, v.commitId, v.description].filter(Boolean) as string[],
          searchHit: false,
          metadata: {},
          issuesCount: 0,
        })),
    );
  }
  return result;
}

function makeLibraryTreeMetadataItems(
  item: Folder,
  options?: {
    level?: number;
    parentIndex: number;
    currentIndex: number;
    hiddenObjects: HiddenObjectsConfig;
    checkHiddenObjects: boolean;
  },
): {classes: LibraryItem[]; container: LibraryItem} | undefined {
  const {
    level = 0,
    parentIndex = -1,
    currentIndex = -1,
    hiddenObjects,
    checkHiddenObjects,
  } = options ?? {};
  if (checkHiddenObjects && hiddenObjects?.metadataFolderIsHidden(item.id)) {
    return undefined;
  }
  const {metadata = {}} = item;
  const classes = Object.entries(metadata)
    .map(([key, value]) => ({key, value}))
    .filter(
      (v) =>
        !checkHiddenObjects ||
        !hiddenObjects ||
        !hiddenObjects.metadataClassIsHidden(item.id, v.key),
    );
  const result: LibraryItem[] = [];
  if (classes.length > 0) {
    const id = getLibraryTreeItemId(LibraryItemType.metadata, item.id);
    const container: LibraryItem = {
      id,
      type: LibraryItemType.metadata,
      name: 'Metadata',
      object: item,
      parentIndex,
      level,
      expanded: false,
      visible: true,
      expandable: true,
      pending: false,
      url: getLibraryTreeItemUrl(LibraryItemType.metadata, item.id),
      interactive: true,
      searchableParts: [],
      searchHit: false,
      metadata: {},
      issuesCount: 0,
    };
    for (const cl of classes) {
      result.push({
        id: getLibraryTreeItemId(LibraryItemType.metadataClass, item.id, cl.key),
        type: LibraryItemType.metadataClass,
        name: cl.key,
        details: `${cl.value}`,
        object: item,
        parentIndex: currentIndex + 1,
        level: level + 1,
        expanded: false,
        visible: true,
        expandable: false,
        pending: false,
        url: getLibraryTreeItemUrl(LibraryItemType.metadataClass, item.id, cl.key),
        interactive: true,
        searchableParts: [cl.key].filter(Boolean),
        searchHit: false,
        metadata: {},
        issuesCount: 0,
      });
    }
    return {
      container,
      classes: result,
    };
  }
  return undefined;
}
