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

import React from 'react';
import PipelinesLibraryContent from './PipelinesLibraryContent';
import {Card, Icon, Input, message, Row, Tree} from 'antd';
import connect from '../../utils/connect';
import localization from '../../utils/localization';
import {observable} from 'mobx';
import SplitPane from 'react-split-pane';
import {inject, observer} from 'mobx-react';
import {
  expandFirstParentWithManyChildren,
  expandItemsByKeys,
  expandItem,
  generateTreeData,
  getExpandedKeys,
  getTreeItemByKey,
  ItemTypes,
  search
} from './model/treeStructureFunctions';
import styles from './PipelinesLibrary.css';

import pipelinesLibrary from '../../models/folders/FolderLoadTree';
import folders from '../../models/folders/Folders';
import dataStorages from '../../models/dataStorage/DataStorages';
import configurations from '../../models/configuration/Configurations';
import ConfigurationUpdate from '../../models/configuration/ConfigurationUpdate';
import pipelines from '../../models/pipelines/Pipelines';
import FolderUpdate from '../../models/folders/FolderUpdate';
import DataStorageUpdate from '../../models/dataStorage/DataStorageUpdate';
import UpdatePipeline from '../../models/pipelines/UpdatePipeline';
import AWSRegionTag from '../special/AWSRegionTag';

const EXPANDED_KEYS_STORAGE_KEY = 'expandedKeys';

@localization.localizedComponent
@connect({
  pipelines,
  pipelinesLibrary,
  folders,
  configurations,
  dataStorages
})
@inject('displayInfo')
@inject(({pipelinesLibrary, pipelines, folders, configurations, dataStorages}, {location}) => {
  let path = location.pathname;
  if (path.startsWith('/')) {
    path = path.substring(1);
  }
  return {
    path,
    pipelines,
    pipelinesLibrary,
    folders,
    configurations,
    dataStorages
  };
})
@observer
export default class PipelinesLibrary extends localization.LocalizedReactComponent {

  state = {
    rootItems: [],
    expandedKeys: [],
    selectedKeys: [],
    currentPath: null
  };

  onExpand = (expandedKeys, {expanded, node}) => {
    const item = getTreeItemByKey(node.props.eventKey, this.state.rootItems);
    if (item) {
      expandItem(item, expanded);
    }
    const keys = getExpandedKeys(this.state.rootItems);
    this.savedExpandedKeys = keys;
    this.setState({expandedKeys: keys});
  };

  onSelect = (selectedKeys, opts) => {
    const {node, selected} = opts;
    if (!selected) {
      selectedKeys.push(node.props.eventKey);
    }
    const item = getTreeItemByKey(node.props.eventKey, this.state.rootItems);
    if (item) {
      expandItem(item, true);
    }
    switch (item.type) {
      case ItemTypes.pipeline:
        this.props.pipelines.invalidatePipeline(item.id);
        break;
      case ItemTypes.folder:
        this.props.folders.invalidateFolder(item.id);
        break;
    }
    this.props.router.push(item.url());
    const keys = getExpandedKeys(this.state.rootItems);
    this.savedExpandedKeys = keys;
    this.setState({selectedKeys, expandedKeys: keys});
  };

  onDrop = async ({node, dragNode}) => {
    const dragItem = getTreeItemByKey(dragNode.props.eventKey, this.state.rootItems);
    const dropItem = getTreeItemByKey(node.props.eventKey, this.state.rootItems);
    if (dragItem.type === ItemTypes.version) {
      message.error(`Cannot drag version '${dragItem.name}'`);
    } else if (dragItem.type === ItemTypes.folder && dropItem.type !== ItemTypes.folder) {
      message.error('You can drop folder only into another folder');
    } else if (dragItem.type === ItemTypes.pipeline && dropItem.type !== ItemTypes.folder) {
      message.error(`You can drop ${this.localizedString('pipeline')} only into a folder`);
    } else if (dragItem.type === ItemTypes.storage && dropItem.type !== ItemTypes.folder) {
      message.error('You can drop storage only into a folder');
    } else if (dragItem.type === ItemTypes.configuration && dropItem.type !== ItemTypes.folder) {
      message.error('You can drop configuration only into a folder');
    } else if (!dragItem.parent || dropItem.id !== dragItem.parent.id) {
      const parentKey = dragItem.parent ? dragItem.parent.key : undefined;
      const hide = message.loading(`Moving '${dragItem.name}' to '${dropItem.name}'`, 0);
      let request;
      let body;
      switch (dragItem.type) {
        case ItemTypes.pipeline:
          request = new UpdatePipeline();
          body = {
            id: dragItem.id,
            name: dragItem.name,
            description: dragItem.description,
            parentFolderId: dropItem.id === 'root' ? undefined : dropItem.id,
            repositoryToken: dragItem.repositoryToken
          };
          break;
        case ItemTypes.folder:
          request = new FolderUpdate();
          body = {
            id: dragItem.id,
            parentId: dropItem.id === 'root' ? undefined : dropItem.id,
            name: dragItem.name
          };
          break;
        case ItemTypes.storage:
          request = new DataStorageUpdate();
          body = {
            id: dragItem.id,
            name: dragItem.name,
            description: dragItem.description,
            storagePolicy: dragItem.storagePolicy,
            parentFolderId: dropItem.id === 'root' ? undefined : dropItem.id
          };
          break;
        case ItemTypes.configuration:
          request = new ConfigurationUpdate();
          body = {
            id: dragItem.id,
            name: dragItem.name,
            description: dragItem.description,
            entries: dragItem.entries,
            parentId: dropItem.id === 'root' ? undefined : dropItem.id
          };
          break;
      }
      if (request && body) {
        await request.send(body);
      }
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        switch (dropItem.type) {
          case ItemTypes.folder:
            if (dropItem.id !== 'root') {
              this.props.folders.invalidateFolder(dropItem.id);
            }
            break;
        }
        switch (dragItem.type) {
          case ItemTypes.folder:
            this.props.folders.invalidateFolder(dragItem.id);
            this.props.folders.load(dragItem.id).fetch();
            break;
          case ItemTypes.pipeline:
            this.props.pipelines.invalidatePipeline(dragItem.id);
            this.props.pipelines.getPipeline(dragItem.id).fetch();
            break;
          case ItemTypes.storage:
            this.props.dataStorages.invalidateCache(dragItem.id);
            this.props.dataStorages.load(dragItem.id).fetch();
            break;
          case ItemTypes.configuration:
            this.props.configurations.invalidateConfigurationCache(dragItem.id);
            this.props.configurations.getConfiguration(dragItem.id).fetch();
            break;
        }
        if (dragItem.parent) {
          switch (dragItem.parent.type) {
            case ItemTypes.folder:
              if (dragItem.parent.id !== 'root') {
                this.props.folders.invalidateFolder(dragItem.parent.id);
              }
              break;
          }
        }
        await this.reloadItem(dropItem.key, true, undefined, true);
        if (parentKey) {
          await this.reloadItem(parentKey, true, undefined, true);
          this.setState({expandedKeys: getExpandedKeys(this.state.rootItems)});
        } else {
          await this.reloadTree(true);
        }
      }
    }
  };

  loadData = (node) => {
    const rootItems = this.state.rootItems;
    const item = getTreeItemByKey(node.props.eventKey, rootItems);
    const setState = (state) => this.setState(state);
    return new Promise(async (resolve) => {
      if (item.type === ItemTypes.pipeline && item.children.length === 0) {
        const versionsRequest = this.props.pipelines.versionsForPipeline(item.id);
        await versionsRequest.fetchIfNeededOrWait();
        item.children = generateTreeData(
          {versions: versionsRequest.value},
          false,
          item,
          getExpandedKeys(rootItems)
        );
        item.isLeaf = !item.children || item.children.length === 0;
      } else if (item.type === ItemTypes.folder && item.id !== 'root' && !item.childrenMetadataLoaded) {
        this.props.folders.invalidateFolder(item.id);
        const childrenRequest = this.props.folders.load(item.id);
        await childrenRequest.fetchIfNeededOrWait();
        item.children = generateTreeData(
          childrenRequest.value,
          false,
          item,
          getExpandedKeys(rootItems)
        );
        item.isLeaf = !item.children || item.children.length === 0;
        item.childrenMetadataLoaded = true;
      } else if (item.type === ItemTypes.folder && item.children.length === 0) {
        item.isLeaf = true;
      } else if (item.type === ItemTypes.version) {
        item.isLeaf = true;
      }
      setState({rootItems});
      resolve();
    });
  };

  renderItemTitle (item) {
    let icon;
    const subIcon = item.locked ? 'lock' : undefined;
    let subTitle;
    switch (item.type) {
      case ItemTypes.pipeline: icon = 'fork'; break;
      case ItemTypes.folder:
        if (item.isProject || (item.objectMetadata && item.objectMetadata.type &&
          (item.objectMetadata.type.value || '').toLowerCase() === 'project')) {
          icon = 'solution';
        } else {
          icon = 'folder';
        }
        break;
      case ItemTypes.version: icon = 'tag'; break;
      case ItemTypes.storage:
        if (item.storageType && item.storageType.toLowerCase() !== 'nfs') {
          icon = 'inbox';
        } else {
          icon = 'hdd';
        }
        subTitle = (
          <AWSRegionTag regionId={item.regionId} size="small" />
        );
        break;
      case ItemTypes.configuration: icon = 'setting'; break;
      case ItemTypes.metadataFolder: icon = 'appstore-o'; break;
      case ItemTypes.metadata: icon = 'appstore-o'; break;
      case ItemTypes.projectHistory: icon = 'clock-circle-o'; break;
    }
    let name = item.type === ItemTypes.metadata ? `${item.name} [${item.amount}]` : item.name;
    if (item.searchResult) {
      name = (
        <span>
          <span>{item.name.substring(0, item.searchResult.index)}</span>
          <span className={styles.searchResult}>
            {
              item.name.substring(
                item.searchResult.index,
                item.searchResult.index + item.searchResult.length
              )
            }
          </span>
          <span>{item.name.substring(item.searchResult.index + item.searchResult.length)}</span>
        </span>
      );
    }
    let treeItemTitleClassName = styles.treeItemTitle;
    if (item.type === ItemTypes.folder && item.locked) {
      treeItemTitleClassName = `${styles.treeItemTitle} ${styles.readonly}`;
    }
    return (
      <span
        id={`pipelines-library-tree-node-${item.key}-name`}
        className={treeItemTitleClassName}>
        {icon && <Icon type={icon} />}
        {subIcon && <Icon type={subIcon} />}
        <span className="name">{name}</span>
        {subTitle}
      </span>
    );
  }

  generateTreeItems (items) {
    return items.map(item => {
      if (item.isLeaf) {
        return (
          <Tree.TreeNode
            className={`pipelines-library-tree-node-${item.key}`}
            title={this.renderItemTitle(item)}
            key={item.key}
            isLeaf={item.isLeaf} />
        );
      } else {
        return (
          <Tree.TreeNode
            className={`pipelines-library-tree-node-${item.key}`}
            title={this.renderItemTitle(item)}
            key={item.key}
            isLeaf={item.isLeaf}>
            {this.generateTreeItems(item.children)}
          </Tree.TreeNode>
        );
      }
    });
  }

  generateTree () {
    return (
      <Tree
        className={styles.libraryTree}
        onSelect={this.onSelect}
        onExpand={this.onExpand}
        checkStrictly={true}
        loadData={this.loadData}
        draggable
        onDrop={this.onDrop}
        expandedKeys={this.state.expandedKeys}
        selectedKeys={this.state.selectedKeys} >
        {this.generateTreeItems(this.state.rootItems)}
      </Tree>
    );
  }

  onSearch = async (text) => {
    await search(text, this.state.rootItems);
    const expandedKeys = getExpandedKeys(this.state.rootItems);
    this.setState({expandedKeys});
  };

  @observable _paneWidth;

  initializeSplitPane = (splitPane) => {
    if (!splitPane) {
      return;
    }
    this._paneWidth = splitPane.splitPane.offsetWidth;
  };

  renderLibrary () {
    return (
      <Card
        id="pipelines-library-tree-container"
        style={{overflowY: 'auto'}}
        className={styles.libraryCard}
        bodyStyle={{padding: 0}}>
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          className={styles.headerRow}>
          <span className={styles.title}>
            Library
          </span>
        </Row>
        <Row id="pipelines-library-tree-search-container" className={styles.searchRow}>
          <Input.Search
            id="pipelines-library-search-input"
            placeholder="Search"
            onSearch={this.onSearch} />
        </Row>
        <Row id="pipelines-library-tree">
          {this.generateTree()}
        </Row>
      </Card>
    );
  }

  render () {
    const detailsView = (
      <PipelinesLibraryContent
        location={this.props.path}
        onReloadTree={(reloadRoot) => this.reloadTree(reloadRoot === undefined ? true : reloadRoot)}>
        {this.props.children}
      </PipelinesLibraryContent>
    );
    if (this.props.displayInfo.libraryCollapsed) {
      return detailsView;
    }

    return (
      <Row id="pipelines-library-container" className={styles.container}>
        <SplitPane
          className="pipelines-library-split-pane"
          ref={this.initializeSplitPane}
          split="vertical"
          minSize={100}
          maxSize={this._paneWidth ? this._paneWidth / 2.0 : 400}
          defaultSize="15%"
          pane1Style={{overflowY: 'auto', display: 'flex', flexDirection: 'column'}}
          pane2Style={{overflowY: 'auto', overflowX: 'hidden', display: 'flex', flexDirection: 'column'}}
          resizerStyle={{
            width: 10,
            margin: '0 -4px',
            cursor: 'col-resize',
            backgroundColor: 'transparent',
            boxSizing: 'border-box',
            backgroundClip: 'padding',
            zIndex: 1
          }}>
          <div id="pipelines-library-split-pane-left" className={styles.subContainer}>
            {this.renderLibrary()}
          </div>
          <div id="pipelines-library-split-pane-right" className={styles.subContainer}>
            {detailsView}
          </div>
        </SplitPane>
      </Row>
    );
  }

  async reloadItem (key, expand, rootItems, reloadRoot = true) {
    if (!key) {
      return;
    }
    if (!rootItems) {
      rootItems = this.state.rootItems;
    }
    const item = getTreeItemByKey(key, rootItems);
    if (!item) {
      return;
    }
    switch (item.type) {
      case ItemTypes.folder:
      case ItemTypes.projectHistory:
        if (item.id === 'root') {
          if (reloadRoot) {
            await this.props.pipelinesLibrary.fetch();
          }
          const childExpandedKeys = getExpandedKeys(item.children);
          item.children = generateTreeData(
            this.props.pipelinesLibrary.value,
            false,
            item,
            childExpandedKeys
          );
          item.isLeaf = item.children ? item.children.length === 0 : true;
        } else {
          const reloadFolderRequest = this.props.folders.load(item.id);
          await reloadFolderRequest.fetchIfNeededOrWait();
          item.name = reloadFolderRequest.value.name;
          const childExpandedKeys = getExpandedKeys(item.children);
          item.children = generateTreeData(
            reloadFolderRequest.value,
            false,
            item,
            childExpandedKeys
          );
          item.isLeaf = item.children.length === 0;
        }
        break;
      case ItemTypes.pipeline: {
        const pipelineRequest = this.props.pipelines.getPipeline(item.id);
        await pipelineRequest.fetchIfNeededOrWait();
        item.name = pipelineRequest.value.name;
        item.description = pipelineRequest.value.description;
        const versionsRequest = this.props.pipelines.versionsForPipeline(item.id);
        await versionsRequest.fetchIfNeededOrWait();
        item.children = generateTreeData(
          {versions: versionsRequest.value},
          false,
          item
        );
      }
        break;
      case ItemTypes.configuration:
        const configurationRequest = this.props.configurations.getConfiguration(item.id);
        await configurationRequest.fetchIfNeededOrWait();
        item.name = configurationRequest.value.name;
        item.description = configurationRequest.value.description;
        break;
      case ItemTypes.storage:
        const dataStorageRequest = this.props.dataStorages.load(item.id);
        await dataStorageRequest.fetchIfNeededOrWait();
        item.name = dataStorageRequest.value.name;
        item.description = dataStorageRequest.value.description;
        break;
      case ItemTypes.metadataFolder:
      case ItemTypes.metadata:
        let parentFolder = item.parent;
        while (parentFolder && parentFolder.type !== ItemTypes.folder && parentFolder.parent) {
          parentFolder = parentFolder.parent;
        }
        if (parentFolder) {
          if (parentFolder.id === 'root') {
            if (reloadRoot) {
              await this.props.pipelinesLibrary.fetch();
            }
            const childExpandedKeys = getExpandedKeys(parentFolder.children);
            parentFolder.children = generateTreeData(
              this.props.pipelinesLibrary.value,
              false,
              parentFolder,
              childExpandedKeys
            );
            parentFolder.isLeaf = parentFolder.children ? parentFolder.children.length === 0 : true;
          } else {
            const reloadFolderRequest = this.props.folders.load(parentFolder.id);
            await reloadFolderRequest.fetchIfNeededOrWait();
            parentFolder.name = reloadFolderRequest.value.name;
            const childExpandedKeys = getExpandedKeys(parentFolder.children);
            parentFolder.children = generateTreeData(
              reloadFolderRequest.value,
              false,
              parentFolder,
              childExpandedKeys
            );
            parentFolder.isLeaf = parentFolder.children.length === 0;
          }
        }
        break;
    }
    if (expand !== undefined) {
      expandItem(item, expand);
    }
  }

  async reloadTree (reload) {
    const parts = this.props.path.split('/');
    let currentPath, placeholderOrPipelineId, idOrVersionName, metadataClass, selectedKey, history;
    for (let i = 0; i < parts.length; i++) {
      if (i === 0) {
        currentPath = parts[i];
        placeholderOrPipelineId = parts[i];
      } else if (i === 1) {
        currentPath += `/${parts[i]}`;
        idOrVersionName = parts[i];
      } else if (i === 2) {
        currentPath += `/${parts[i]}`;
        if ((placeholderOrPipelineId || '').toLowerCase() === ItemTypes.metadata) {
          metadataClass = parts[i];
        } else if ((placeholderOrPipelineId || '').toLowerCase() === 'folder') {
          history = (parts[i] || '').toLowerCase() === 'history';
        }
      }
    }
    let rootItems = this.state.rootItems || [];
    let selectedItem;
    const findVersionFn = (parentPipeline) => {
      if (idOrVersionName) {
        for (let i = 0; i < parentPipeline.children.length; i++) {
          const child = parentPipeline.children[i];
          if (child.type === ItemTypes.version &&
            child.name.toLowerCase() === idOrVersionName.toLowerCase()) {
            selectedKey = child.key;
            break;
          }
        }
      }
      if (!selectedKey) {
        selectedKey = parentPipeline.key;
      }
    };
    switch ((placeholderOrPipelineId || '').toLowerCase()) {
      case 'storage':
        if (idOrVersionName) {
          selectedKey = `${ItemTypes.storage}_${idOrVersionName}`;
        }
        break;
      case 'configuration':
        if (idOrVersionName) {
          selectedKey = `${ItemTypes.configuration}_${idOrVersionName}`;
        }
        break;
      case 'metadatafolder':
        if (idOrVersionName) {
          selectedKey = `${ItemTypes.metadataFolder}_${idOrVersionName}metadataFolder`;
        }
        break;
      case 'metadata':
        if (idOrVersionName) {
          selectedKey = `${ItemTypes.metadata}_${idOrVersionName}metadataFolder${metadataClass}`;
        }
        break;
      case 'folder':
      case 'library':
      case '':
        if (idOrVersionName) {
          selectedKey = `${ItemTypes.folder}_${idOrVersionName}`;
        } else {
          selectedKey = `${ItemTypes.folder}_root`;
        }
        break;
      default:
        selectedKey = `${ItemTypes.pipeline}_${placeholderOrPipelineId}`;
        break;
    }
    if (reload || rootItems.length === 0) {
      await this.props.pipelinesLibrary.fetch();
      const rootElements = [{
        id: 'root',
        name: 'Library',
        ...this.props.pipelinesLibrary.value
      }];
      rootItems = generateTreeData({childFolders: rootElements}, false, null);
      const savedExpandedKeys = this.savedExpandedKeys;
      if (savedExpandedKeys.length > 0) {
        expandItemsByKeys(rootItems, savedExpandedKeys);
      } else {
        expandFirstParentWithManyChildren(rootItems[0]);
      }
    }
    await this.reloadItem(selectedKey, undefined, rootItems, false);
    if ((placeholderOrPipelineId || '').toLowerCase() === 'folder' && idOrVersionName && history) {
      selectedKey = `${ItemTypes.projectHistory}_${idOrVersionName}`;
    }
    selectedItem = getTreeItemByKey(selectedKey, rootItems);
    if (selectedItem) {
      if (selectedItem.type === ItemTypes.pipeline) {
        findVersionFn(selectedItem);
      }
      expandItem(selectedItem, true);
    }
    this.setState({
      rootItems: rootItems,
      selectedKeys: selectedKey ? [selectedKey] : [],
      expandedKeys: getExpandedKeys(rootItems),
      currentPath: currentPath
    });
  }

  get savedExpandedKeys () {
    const savedKeys = JSON.parse(localStorage.getItem(EXPANDED_KEYS_STORAGE_KEY));
    return savedKeys && savedKeys.length > 0 ? savedKeys : [];
  }

  set savedExpandedKeys (keys) {
    localStorage.setItem(EXPANDED_KEYS_STORAGE_KEY, JSON.stringify(keys));
  };

  componentDidUpdate (prevProps) {
    if (prevProps.path !== this.props.path) {
      return this.reloadTree(false);
    }
  }

  componentDidMount () {
    (async () => {
      await this.reloadTree(true);
      if (this.state.rootItems && this.props.path === '') {
        const rootItem = expandFirstParentWithManyChildren(this.state.rootItems[0]);
        if (rootItem && rootItem.type === ItemTypes.folder && rootItem.id !== 'root') {
          this.props.router.push(`/folder/${rootItem.id}`);
        }
      }
    })();
  }
}
