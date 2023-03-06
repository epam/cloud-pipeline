/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const folder = 'folder';
const pipeline = 'pipeline';
const versionedStorage = 'versionedStorage';
const version = 'version';
const storage = 'storage';
const configuration = 'configuration';
const metadata = 'metadata';
const metadataFolder = 'metadataFolder';
const projectHistory = 'projectHistory';

const fireCloud = 'fireCloud';
const fireCloudMethod = 'fireCloudMethod';
const fireCloudMethodVersion = 'fireCloudMethodVersion';
const fireCloudMethodConfiguration = 'fireCloudMethodVersion';

export const ItemTypes = {
  folder,
  pipeline,
  version,
  versionedStorage,
  storage,
  configuration,
  metadata,
  metadataFolder,
  projectHistory,
  fireCloud,
  fireCloudMethod,
  fireCloudMethodVersion,
  fireCloudMethodConfiguration
};

function generateUrl (item) {
  if (!item) {
    return '/library';
  }
  switch (item.type) {
    case ItemTypes.folder: {
      if (!item.id) {
        return '/library';
      }
      const itemId = `${item.id}`.toLowerCase();
      if (['root', 'pipelines', 'storages'].indexOf(itemId) === -1) {
        return `/folder/${itemId}`;
      }
      switch (item.id.toLowerCase()) {
        case 'pipelines': return '/pipelines';
        case 'storages': return '/storages';
        case 'root':
        default:
          return '/library';
      }
    }
    case ItemTypes.pipeline: return `/${item.id}`;
    case ItemTypes.version: return `/${item.parentId}/${item.name}`;
    case ItemTypes.versionedStorage: return `/vs/${item.id}`;
    case ItemTypes.storage: return `/storage/${item.id}`;
    case ItemTypes.configuration: return `/configuration/${item.id}`;
    case ItemTypes.metadata: return `/folder/${item.folderId}/metadata/${item.name}`;
    case ItemTypes.metadataFolder: return `/folder/${item.parentId}/metadata`;
    case ItemTypes.projectHistory: return `/folder/${item.id}/history`;
    default:
      return '/';
  }
}

function nameSorter (pA, pB) {
  if (pA.name.toLowerCase() > pB.name.toLowerCase()) {
    return 1;
  } else if (pA.name.toLowerCase() < pB.name.toLowerCase()) {
    return -1;
  }
  return 0;
}

/**
 * @typedef {Object} LibraryTree
 * @property {*[]} [pipelines]
 * @property {*[]} [childFolders]
 * @property {*[]} [versions]
 * @property {*[]} [storages]
 * @property {*[]} [configurations]
 * @property {*} [metadata]
 * @property {number|string} [id]
 * @property {*} [objectMetadata]
 * @property {*} [fireCloud]
 */

/**
 * @typedef {Object} GenerateTreeDataOptions
 * @property {boolean} [ignoreChildren=false]
 * @property {*} [parent]
 * @property {string[]} [expandedKeys]
 * @property {string[]} [types]
 * @property {function(item, type)} [filter]
 * @property {boolean} [sortRoot=false]
 */

/**
 * @param {LibraryTree} libraryTree
 * @param {GenerateTreeDataOptions} [options]
 * @returns {string|*[]}
 */
export function generateTreeData (
  libraryTree,
  options = {}
) {
  const {
    pipelines,
    childFolders,
    versions,
    storages,
    configurations,
    metadata,
    id,
    objectMetadata,
    fireCloud
  } = libraryTree || {};
  const {
    ignoreChildren = false,
    parent = null,
    expandedKeys = [],
    types = undefined,
    filter = (item, type) => true,
    sortRoot = true
  } = options;
  const children = [];
  const pipelinesSorted = (pipelines || [])
    .filter(pipeline => !/^versioned_storage$/i.test(pipeline.pipelineType))
    .map(f => f);
  if (sortRoot) {
    pipelinesSorted.sort(nameSorter);
  }
  const versionedStoragesSorted = (pipelines || [])
    .filter(pipeline => /^versioned_storage$/i.test(pipeline.pipelineType))
    .map(f => f);
  if (sortRoot) {
    versionedStoragesSorted.sort(nameSorter);
  }
  const childFoldersSorted = (childFolders || []).map(f => f);
  if (sortRoot) {
    childFoldersSorted.sort(nameSorter);
  }
  const childStoragesSorted = (storages || []).map(f => f);
  if (sortRoot) {
    childStoragesSorted.sort(nameSorter);
  }
  const configurationsSorted = (configurations || []).map(f => f);
  if (sortRoot) {
    configurationsSorted.sort(nameSorter);
  }
  if (fireCloud && (!types || types.indexOf(ItemTypes.fireCloud) >= 0)) {
    const fireCloudItem = {
      id: ItemTypes.fireCloud,
      key: ItemTypes.fireCloud,
      name: 'FireCloud',
      type: ItemTypes.fireCloud,
      url () {
        return generateUrl(this);
      }
    };
    if (
      fireCloud.methods &&
      fireCloud.methods.length > 0 &&
      (!types || types.indexOf(ItemTypes.fireCloudMethod) >= 0)
    ) {
      fireCloudItem.children = [];
      for (let i = 0; i < fireCloud.methods.length; i++) {
        const method = fireCloud.methods[i];
        const fireCloudMethod = {
          id: method.name,
          key: `${ItemTypes.fireCloudMethod}_${method.namespace}_${method.name}`,
          name: method.name,
          namespace: method.namespace,
          type: ItemTypes.fireCloudMethod,
          parent: fireCloudItem
        };
        if (
          method.snapshotIds &&
          method.snapshotIds.length > 0 &&
          (!types || types.indexOf(ItemTypes.fireCloudMethodVersion) >= 0)
        ) {
          fireCloudMethod.children = [];
          for (let j = 0; j < method.snapshotIds.length; j++) {
            const snapshotId = method.snapshotIds[j];
            const fireCloudMethodVersion = {
              id: snapshotId,
              key: `${ItemTypes.fireCloudMethodVersion}_${snapshotId}`,
              name: snapshotId,
              type: ItemTypes.fireCloudMethodVersion,
              parent: fireCloudMethod,
              isLeaf: true
            };
            fireCloudMethod.children.push(fireCloudMethodVersion);
          }
        }
        fireCloudMethod.isLeaf = !fireCloudMethod.children || fireCloudMethod.children.length === 0;
        fireCloudItem.children.push(fireCloudMethod);
      }
    }
    fireCloudItem.isLeaf = !fireCloudItem.children || fireCloudItem.children.length === 0;
    children.push(fireCloudItem);
  }
  if (childFoldersSorted.length) {
    for (let i = 0; i < childFoldersSorted.length; i++) {
      if (!filter(childFoldersSorted[i], ItemTypes.folder)) {
        continue;
      }
      const isProject = !!(childFoldersSorted[i].objectMetadata &&
        childFoldersSorted[i].objectMetadata.type &&
        childFoldersSorted[i].objectMetadata.type.value &&
        childFoldersSorted[i].objectMetadata.type.value.toLowerCase() === 'project');
      const folder = {
        ...childFoldersSorted[i],
        id: childFoldersSorted[i].id,
        key: `${ItemTypes.folder}_${childFoldersSorted[i].id}`,
        name: childFoldersSorted[i].name,
        type: ItemTypes.folder,
        entityId: childFoldersSorted[i].id,
        entityClass: 'FOLDER',
        parentId: childFoldersSorted[i].parentId,
        path: parent ? `${parent.path}/${childFoldersSorted[i].name}` : childFoldersSorted[i].name,
        parent: parent,
        createdDate: childFoldersSorted[i].createdDate,
        mask: childFoldersSorted[i].mask,
        locked: childFoldersSorted[i].locked,
        objectMetadata: childFoldersSorted[i].objectMetadata,
        hasMetadata: childFoldersSorted[i].hasMetadata,
        issuesCount: childFoldersSorted[i].issuesCount,
        isProject,
        url () {
          return generateUrl(this);
        },
        parentUrl () {
          return generateUrl(this.parent);
        }
      };
      folder.children = ignoreChildren && (!types || types.length === 0)
        ? undefined
        : generateTreeData(
          childFoldersSorted[i],
          {
            ...options,
            parent: folder,
            sortRoot: true
          }
        );
      folder.isLeaf = ignoreChildren
        ? true
        : folder.children.length === 0;
      folder.expanded = expandedKeys.indexOf(folder.key) >= 0 && folder.children.length > 0;
      if (
        !types ||
        types.indexOf(ItemTypes.folder) >= 0 ||
        (folder.children && folder.children.length > 0)
      ) {
        children.push(folder);
      }
      if (ignoreChildren) {
        folder.children = undefined;
      }
    }
  }
  if (childStoragesSorted.length && (!types || types.indexOf(ItemTypes.storage) >= 0)) {
    for (let i = 0; i < childStoragesSorted.length; i++) {
      if (!filter(childStoragesSorted[i], ItemTypes.storage)) {
        continue;
      }
      const storage = {
        ...childStoragesSorted[i],
        id: childStoragesSorted[i].id,
        key: `${ItemTypes.storage}_${childStoragesSorted[i].id}`,
        name: childStoragesSorted[i].name,
        regionId: childStoragesSorted[i].regionId,
        type: ItemTypes.storage,
        entityId: childStoragesSorted[i].id,
        entityClass: 'DATA_STORAGE',
        storageType: childStoragesSorted[i].type,
        parentId: childStoragesSorted[i].parentFolderId,
        parent: parent,
        path: childStoragesSorted[i].path,
        createdDate: childStoragesSorted[i].createdDate,
        mask: childStoragesSorted[i].mask,
        locked: childStoragesSorted[i].locked,
        storagePolicy: childStoragesSorted[i].storagePolicy,
        policySupported: childStoragesSorted[i].policySupported,
        shared: !!childStoragesSorted[i].shared,
        description: childStoragesSorted[i].description,
        objectMetadata: childStoragesSorted[i].objectMetadata,
        hasMetadata: childStoragesSorted[i].hasMetadata,
        issuesCount: childStoragesSorted[i].issuesCount,
        fileShareMountId: childStoragesSorted[i].fileShareMountId,
        mountPoint: childStoragesSorted[i].mountPoint,
        mountOptions: childStoragesSorted[i].mountOptions,
        sensitive: childStoragesSorted[i].sensitive,
        sourceStorageId: childStoragesSorted[i].sourceStorageId,
        url () {
          return generateUrl(this);
        },
        parentUrl () {
          return generateUrl(this.parent);
        }
      };
      storage.children = ignoreChildren
        ? undefined
        : generateTreeData(
          childStoragesSorted[i],
          {
            ...options,
            ignoreChildren: false,
            parent: storage,
            sortRoot: true
          }
        );
      storage.isLeaf = ignoreChildren
        ? true
        : storage.children.length === 0;
      storage.expanded = expandedKeys.indexOf(storage.key) >= 0 && storage.children.length > 0;
      children.push(storage);
    }
  }
  if (
    versionedStoragesSorted.length &&
    (!types || types.indexOf(ItemTypes.versionedStorage) >= 0)
  ) {
    for (let i = 0; i < versionedStoragesSorted.length; i++) {
      if (!filter(versionedStoragesSorted[i], ItemTypes.versionedStorage)) {
        continue;
      }
      const versionedStorage = {
        ...versionedStoragesSorted[i],
        id: versionedStoragesSorted[i].id,
        key: `${ItemTypes.versionedStorage}_${versionedStoragesSorted[i].id}`,
        name: versionedStoragesSorted[i].name,
        type: ItemTypes.versionedStorage,
        entityId: versionedStoragesSorted[i].id,
        entityClass: 'PIPELINE',
        parent: parent,
        isLeaf: true,
        description: versionedStoragesSorted[i].description,
        createdDate: versionedStoragesSorted[i].createdDate,
        repository: versionedStoragesSorted[i].repository,
        repositoryToken: versionedStoragesSorted[i].repositoryToken,
        mask: versionedStoragesSorted[i].mask,
        locked: versionedStoragesSorted[i].locked,
        objectMetadata: versionedStoragesSorted[i].objectMetadata,
        hasMetadata: versionedStoragesSorted[i].hasMetadata,
        issuesCount: versionedStoragesSorted[i].issuesCount,
        url () {
          return generateUrl(this);
        },
        parentUrl () {
          return generateUrl(this.parent);
        }
      };
      versionedStorage.children = undefined;
      versionedStorage.expanded = false;
      children.push(versionedStorage);
    }
  }
  if (pipelinesSorted.length && (!types || types.indexOf(ItemTypes.pipeline) >= 0)) {
    for (let i = 0; i < pipelinesSorted.length; i++) {
      if (!filter(pipelinesSorted[i], ItemTypes.pipeline)) {
        continue;
      }
      const pipeline = {
        ...pipelinesSorted[i],
        id: pipelinesSorted[i].id,
        key: `${ItemTypes.pipeline}_${pipelinesSorted[i].id}`,
        name: pipelinesSorted[i].name,
        type: ItemTypes.pipeline,
        entityId: pipelinesSorted[i].id,
        entityClass: 'PIPELINE',
        parent: parent,
        isLeaf: (types && types.indexOf(ItemTypes.version) === -1),
        description: pipelinesSorted[i].description,
        createdDate: pipelinesSorted[i].createdDate,
        repository: pipelinesSorted[i].repository,
        repositoryToken: pipelinesSorted[i].repositoryToken,
        mask: pipelinesSorted[i].mask,
        locked: pipelinesSorted[i].locked,
        objectMetadata: pipelinesSorted[i].objectMetadata,
        hasMetadata: pipelinesSorted[i].hasMetadata,
        issuesCount: pipelinesSorted[i].issuesCount,
        url () {
          return generateUrl(this);
        },
        parentUrl () {
          return generateUrl(this.parent);
        }
      };
      pipeline.children = ignoreChildren || (types && types.indexOf(ItemTypes.version) === -1)
        ? undefined
        : generateTreeData(
          pipelinesSorted[i],
          {
            ...options,
            parent: pipeline,
            ignoreChildren: false,
            sortRoot: true
          }
        );
      pipeline.expanded = expandedKeys.indexOf(pipeline.key) >= 0 && pipeline.children.length > 0;
      children.push(pipeline);
    }
  }
  if (versions && versions.length && (!types || types.indexOf(ItemTypes.version) >= 0)) {
    for (let i = 0; i < versions.length; i++) {
      if (!filter(versions[i], ItemTypes.version, parent)) {
        continue;
      }
      children.push({
        ...versions[i],
        id: versions[i].commitId,
        key: `${ItemTypes.version}_${parent ? parent.id : ''}_${versions[i].commitId}`,
        name: versions[i].name,
        author: versions[i].author,
        type: ItemTypes.version,
        entityId: parent && parent.id,
        entityClass: 'PIPELINE',
        children: ignoreChildren ? undefined : [],
        parent: parent,
        parentId: parent && parent.id,
        isLeaf: true,
        expanded: false,
        description: versions[i].message,
        draft: versions[i].draft,
        createdDate: versions[i].createdDate,
        mask: 7,
        url () {
          return generateUrl(this);
        },
        parentUrl () {
          return generateUrl(this.parent);
        }
      });
    }
  }
  if (configurations && configurations.length &&
    (!types || types.indexOf(ItemTypes.configuration) >= 0)) {
    for (let i = 0; i < configurationsSorted.length; i++) {
      if (!filter(configurationsSorted[i], ItemTypes.configuration)) {
        continue;
      }
      const configuration = {
        ...configurationsSorted[i],
        id: configurationsSorted[i].id,
        key: `${ItemTypes.configuration}_${configurationsSorted[i].id}`,
        name: configurationsSorted[i].name,
        description: configurationsSorted[i].description,
        type: ItemTypes.configuration,
        entityId: configurationsSorted[i].id,
        entityClass: 'CONFIGURATION',
        parentId: configurationsSorted[i].parent ? configurationsSorted[i].parent.id : undefined,
        parent: parent,
        entries: configurationsSorted[i].entries.map(e => e),
        createdDate: configurationsSorted[i].createdDate,
        mask: configurationsSorted[i].mask,
        locked: configurationsSorted[i].locked,
        objectMetadata: configurationsSorted[i].objectMetadata,
        hasMetadata: configurationsSorted[i].hasMetadata,
        issuesCount: configurationsSorted[i].issuesCount,
        url () {
          return generateUrl(this);
        },
        parentUrl () {
          return generateUrl(this.parent);
        }
      };
      configuration.children = undefined;
      configuration.isLeaf = true;
      configuration.expanded = false;
      children.push(configuration);
    }
  }
  if (metadata && Object.keys(metadata).length &&
    (!types || types.indexOf(ItemTypes.metadata) >= 0) &&
    (!filter || filter({id}, ItemTypes.metadataFolder))
  ) {
    const metadataFolder = {
      id: `${id}/metadata`,
      key: `${ItemTypes.metadataFolder}_${id}/metadata`,
      folderId: id,
      name: 'Metadata',
      type: ItemTypes.metadataFolder,
      children: [],
      parent: parent,
      parentId: id,
      isLeaf: false,
      locked: parent ? parent.locked : false,
      url () {
        return generateUrl(this);
      },
      parentUrl () {
        return generateUrl(this.parent);
      }
    };

    const metadataChildren = [];
    for (let key in metadata) {
      if (
        filter && (
          !filter({id: key}, ItemTypes.metadata, {id}) ||
          !filter({id: key}, ItemTypes.metadata)
        )
      ) {
        continue;
      }
      metadataChildren.push({
        id: `${id}/metadata/${key}`,
        key: `${ItemTypes.metadata}_${id}/metadata/${key}`,
        name: key,
        type: ItemTypes.metadata,
        children: undefined,
        parent: metadataFolder,
        folderId: id,
        parentId: metadataFolder && metadataFolder.id,
        isLeaf: true,
        amount: metadata[key],
        locked: metadataFolder ? metadataFolder.locked : false,
        url () {
          return generateUrl(this);
        },
        parentUrl () {
          return generateUrl(this.parent);
        }
      });
    }
    metadataFolder.children = ignoreChildren
      ? undefined
      : metadataChildren;
    metadataFolder.metadataClasses = metadataChildren;
    children.push(metadataFolder);
  }

  if ((!types || types.indexOf(ItemTypes.projectHistory) >= 0) && objectMetadata &&
    objectMetadata.type && objectMetadata.type.value &&
    objectMetadata.type.value.toLowerCase() === 'project') {
    const projectHistory = {
      id: id,
      key: `${ItemTypes.projectHistory}_${id}`,
      name: 'History',
      type: ItemTypes.projectHistory,
      parent: parent,
      parentId: id,
      isLeaf: true,
      locked: false,
      url () {
        return generateUrl(this);
      },
      parentUrl () {
        return generateUrl(this.parent);
      }
    };
    children.push(projectHistory);
  }

  if (parent && (parent.type || '') === ItemTypes.folder) {
    parent.isProject = !!(objectMetadata && objectMetadata.type && objectMetadata.type.value &&
      objectMetadata.type.value.toLowerCase() === 'project');
  }
  for (let i = 0; i < children.length; i++) {
    children[i].searchHit = true;
  }
  return children;
}

/**
 * @param {*[]} treeItems
 * @param {{inlineMetadata: boolean?, preferences: *}} [options]
 */
export function formatTreeItems (treeItems = [], options = {}) {
  const {
    preferences,
    inlineMetadata = preferences ? preferences.inlineMetadataEntities : false
  } = options;
  return (treeItems || []).reduce(
    (result, item) => {
      if (item.type === ItemTypes.metadataFolder && inlineMetadata) {
        return [...result, ...(item.children || item.metadataClasses || [])];
      }
      return [...result, item];
    },
    []
  );
}

export function getTreeItemInfoByKey (key) {
  const [type, ...idParts] = key.split('_');
  const id = idParts.length === 0 ? type : idParts.join('_');
  return {
    id,
    type
  };
}

export function getTreeItemByKey (key, items) {
  let item;
  const info = getTreeItemInfoByKey(key);
  if (items && items.length > 0) {
    for (let i = 0; i < items.length; i++) {
      if (
        (`${items[i].id}` === `${info.id}` && items[i].type === info.type) ||
        items[i].key === key
      ) {
        item = items[i];
        break;
      }
      item = getTreeItemByKey(key, items[i].children);
      if (item) {
        break;
      }
    }
  }
  return item;
}

export function getExpandedKeys (items) {
  const result = [];
  if (items && items.length > 0) {
    for (let i = 0; i < items.length; i++) {
      if (items[i].expanded) {
        result.push(items[i].key, ...getExpandedKeys(items[i].children));
      }
    }
  }
  return result;
}

export function expandItem (item, expand) {
  item.expanded = expand;
  if (item.parent && expand) {
    expandItem(item.parent, expand);
  }
}

export async function search (value, items, parentFound = false) {
  if (!value || value === '' || !items) {
    if (items) {
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        item.searchResult = undefined;
        item.searchHit = true;
        await search(value, item.children);
      }
    }
    return;
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    if (item.type === ItemTypes.version) {
      continue;
    }
    const index = item.name.toLowerCase().indexOf(value.toLowerCase());
    item.searchHit = parentFound || index >= 0;
    if (index >= 0) {
      item.searchResult = {
        index: index,
        length: value.length
      };
      if (item.children && item.children.length > 0) {
        expandItem(item, true);
      } else if (item.parent) {
        expandItem(item.parent, true);
      }
    } else {
      item.searchResult = undefined;
      expandItem(item, false);
    }
    await search(value, item.children, item.searchHit);
    item.searchHit = item.searchHit || !!(item.children || []).find(e => e.searchHit);
  }
}

export function expandItemsByKeys (items, expandedKeys) {
  items.forEach(item => {
    if (expandedKeys.includes(item.key)) {
      expandItem(item, true);
    }
    if (item.children && item.children.length > 0) {
      expandItemsByKeys(item.children, expandedKeys);
    }
  });
}

export function expandFirstParentWithManyChildren (item) {
  item.expanded = true;
  if (item.children && item.children.length === 1 && item.children[0].type === ItemTypes.folder) {
    return expandFirstParentWithManyChildren(item.children[0]);
  }
  return item;
}

export function findPath (key, items, parentPath) {
  if (!items) {
    return null;
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    const prefix = parentPath || [];
    if (item.key === key) {
      return [
        ...prefix,
        {name: item.name, id: item.id, type: item.type, key: item.key, url: generateUrl(item)}
      ];
    } else if (item.children && item.children.length > 0) {
      const result =
        findPath(
          key,
          item.children,
          [
            ...prefix,
            {name: item.name, id: item.id, type: item.type, key: item.key, url: generateUrl(item)}
          ]
        );
      if (result) {
        return result;
      }
    }
  }
  return null;
}
