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
import {inject, observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {Alert, AutoComplete, Button, Col, Input, Modal, Row, Tree, Splitter} from 'antd';
import {AppstoreOutlined, FolderOutlined} from '@ant-design/icons';
import Folder from '../../browser/Folder';
import Metadata from '../../browser/Metadata';
import MetadataFolder from '../../browser/MetadataFolder';
import {
  expandItem,
  formatTreeItems,
  generateTreeData,
  getExpandedKeys,
  getTreeItemByKey,
  ItemTypes,
  search,
} from '../../model/treeStructureFunctions';

import styles from './Browser.module.css';
import HiddenObjects from '../../../../utils/hidden-objects';

function isSelectionEqual(a, b) {
  if (a === b) {
    return true;
  }
  if (!a || !b) {
    return !a && !b;
  }
  const keysA = Object.keys(a);
  const keysB = Object.keys(b);
  if (keysA.length === 0 && keysB.length === 0) {
    return true;
  }
  if (keysA.length !== keysB.length) {
    return false;
  }
  const entitiesIdsA = a.entitiesIds || [];
  const entitiesIdsB = b.entitiesIds || [];
  return (
    a.folderId === b.folderId &&
    a.metadataClassName === b.metadataClassName &&
    entitiesIdsA.length === entitiesIdsB.length &&
    entitiesIdsA.every((id, index) => id === entitiesIdsB[index])
  );
}

@inject('folders', 'preferences', 'pipelinesLibrary')
@inject(({routing, folders, pipelinesLibrary}, params) => {
  if (!params.initialFolderId) {
    return {
      tree: pipelinesLibrary,
    };
  }
  return {
    tree: folders.loadWithoutMetadata(params.initialFolderId ? params.initialFolderId : null),
  };
})
@HiddenObjects.injectTreeFilter
@observer
export default class MetadataBrowser extends React.Component {
  static propTypes = {
    initialFolderId: PropTypes.number,
    initialActiveFolderId: PropTypes.number,
    visible: PropTypes.bool,
    onSelect: PropTypes.func,
    onCancel: PropTypes.func,
    rootEntityId: PropTypes.string,
    currentMetadataEntity: PropTypes.array,
    readOnly: PropTypes.bool,
    hideExpansionExpression: PropTypes.bool,
    selection: PropTypes.object,
    browseLibrary: PropTypes.bool,
    disableMetadataFolderSelection: PropTypes.bool,
  };

  rootItems = null;

  state = {
    folderId: null,
    expandedKeys: [],
    selectedKeys: [],
    initialSelection: null,
    selectedMetadata: [],
    selectedMetadataClassEntity: [],
    treeReady: false,
    isMetadataFolder: false,
    isMetadata: false,
    metadataClassName: null,
    expansionExpression: '',
    filteredEntityFields: [],
    search: null,
  };

  onClearSelectionClicked = () => {
    this.setState({
      selectedMetadata: [],
      selectedMetadataClassEntity: [],
    });
  };

  onCancelClicked = () => {
    if (this.props.onCancel) {
      this.props.onCancel();
    }
    this.setState({
      folderId: null,
      expandedKeys: [],
      selectedKeys: [],
      selectedMetadata: [],
      selectedMetadataClassEntity: [],
      isMetadataFolder: false,
      isMetadata: false,
      expansionExpression: '',
      filteredEntityFields: [],
    });
  };

  onSubmitClicked = async () => {
    if (this.props.onSelect) {
      let selectedMetadataClassEntity = null;
      let folderId = null;
      if (this.state.selectedMetadataClassEntity.length) {
        selectedMetadataClassEntity = this.state.selectedMetadataClassEntity[0].name;
        folderId = this.state.selectedMetadataClassEntity[0].parent.parentId;
      }
      const entitiesIds = this.state.selectedMetadata.slice();
      const metadataLibraryLocation = {
        folderId: this.state.folderId,
        metadataClassName: this.state.metadataClassName,
      };
      this.props.onSelect(
        entitiesIds,
        selectedMetadataClassEntity,
        this.state.expansionExpression,
        folderId,
        metadataLibraryLocation,
      );
    }
  };

  get isSelectAvailable() {
    return (
      this.state.selectedMetadata.length > 0 || this.state.selectedMetadataClassEntity.length > 0
    );
  }

  get selectedItemsCount() {
    if (this.state.selectedMetadata.length > 0) {
      return this.state.selectedMetadata.length;
    } else if (this.state.selectedMetadataClassEntity.length > 0) {
      return this.state.selectedMetadataClassEntity.length;
    }
    return null;
  }

  get isExpansionExpressionAvailable() {
    let currentRootEntity;
    if (this.state.isMetadata) {
      [currentRootEntity] = this.props.currentMetadataEntity.filter(
        (matadataEntity) => `${matadataEntity.metadataClass.name}` === this.state.metadataClassName,
      );
    } else if (this.state.isMetadataFolder && this.state.selectedMetadataClassEntity.length) {
      const currentMetadataClassEntity = this.state.selectedMetadataClassEntity[0];
      [currentRootEntity] = this.props.currentMetadataEntity.filter(
        (matadataEntity) =>
          `${matadataEntity.metadataClass.name}` === currentMetadataClassEntity.name,
      );
    }
    return currentRootEntity
      ? `${currentRootEntity.metadataClass.id}` !== this.props.rootEntityId
      : false;
  }

  renderItemTitle(item) {
    let IconComponent;
    switch (item.type) {
      case ItemTypes.folder:
        IconComponent = FolderOutlined;
        break;
      case ItemTypes.metadata:
        IconComponent = AppstoreOutlined;
        break;
      case ItemTypes.metadataFolder:
        IconComponent = AppstoreOutlined;
        break;
    }
    let name = item.name;
    if (item.searchResult) {
      name = (
        <span>
          <span>{item.name.substring(0, item.searchResult.index)}</span>
          <span className={styles.searchResult}>
            {item.name.substring(
              item.searchResult.index,
              item.searchResult.index + item.searchResult.length,
            )}
          </span>
          <span>{item.name.substring(item.searchResult.index + item.searchResult.length)}</span>
        </span>
      );
    }
    return (
      <span id={`pipelines-library-tree-node-${item.key}-name`} className={styles.treeItemTitle}>
        {IconComponent && <IconComponent />}
        {name}
      </span>
    );
  }

  getTreeData(items) {
    if (!items) {
      return [];
    }
    return formatTreeItems(items, {preferences: this.props.preferences}).map((item) => ({
      key: item.key,
      title: this.renderItemTitle(item),
      isLeaf: item.isLeaf,
      className: `pipelines-library-tree-node-${item.key}`,
      ...(item.children && !item.isLeaf ? {children: this.getTreeData(item.children)} : {}),
    }));
  }

  onExpand = (expandedKeys, {expanded, node}) => {
    const item = getTreeItemByKey(node.key, this.rootItems);
    if (item) {
      expandItem(item, expanded);
    }
    this.setState({expandedKeys: getExpandedKeys(this.rootItems)});
  };

  onSelect = (selectedKeys, {node}) => {
    const item = getTreeItemByKey(node.key, this.rootItems);
    if (!item) {
      return;
    }
    if (item.type === ItemTypes.metadataFolder) {
      this.onSelectMetadataFolder(item.id);
    } else if (item.type === ItemTypes.metadata) {
      this.onSelectMetadata(item);
    } else {
      this.onSelectFolder(item.id);
    }
    this.onExpand(this.state.expandedKeys, {
      expanded: !node.isLeaf && !this.state.expandedKeys.includes(node.key),
      node,
    });
  };

  generateTree() {
    const {tree, initialFolderId} = this.props;
    if (tree.loaded && !tree.pending && !tree.error && !this.rootItems) {
      let folder = {
        id: undefined,
        key: `${ItemTypes.folder}_root`,
        name: 'Library',
        type: ItemTypes.folder,
        parentId: null,
        parent: null,
      };
      if (initialFolderId) {
        folder = {
          id: this.props.tree.value.id,
          key: `${ItemTypes.folder}_${this.props.tree.value.id}`,
          name: this.props.tree.value.name,
          type: ItemTypes.folder,
          parentId: null,
          parent: null,
          createdDate: this.props.tree.value.createdDate,
          mask: this.props.tree.value.mask,
        };
      }
      folder.children = generateTreeData(tree.value, {
        parent: folder,
        types: [ItemTypes.metadata],
        filter: this.props.hiddenObjectsTreeFilter(),
      });
      folder.isLeaf = folder.children.length === 0;
      folder.expanded = true;

      this.rootItems = [folder];
    }
    return (
      <Tree
        className={styles.libraryTree}
        treeData={this.getTreeData(this.rootItems)}
        onSelect={this.onSelect}
        onExpand={this.onExpand}
        checkStrictly
        expandedKeys={this.state.expandedKeys}
        selectedKeys={this.state.selectedKeys}
      />
    );
  }

  onSelectFolder = (id) => {
    let expandedKeys = this.state.expandedKeys;
    if (this.rootItems) {
      const item = getTreeItemByKey(`${ItemTypes.folder}_${id}`, this.rootItems);
      if (item) {
        expandItem(item, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState({
      isMetadataFolder: false,
      isMetadata: false,
      metadataClassName: null,
      selectedMetadata: [],
      selectedMetadataClassEntity: [],
      folderId: id,
      selectedKeys: [`${ItemTypes.folder}_${id}`],
      expandedKeys,
    });
  };

  onSelectMetadataFolder = (id) => {
    let expandedKeys = this.state.expandedKeys;
    const intId = parseInt(id, 10);
    if (this.rootItems) {
      const item = getTreeItemByKey(`${ItemTypes.metadataFolder}_${id}/metadata`, this.rootItems);
      if (item) {
        expandItem(item, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState({
      isMetadataFolder: true,
      isMetadata: false,
      metadataClassName: null,
      selectedMetadata: [],
      folderId: intId,
      selectedKeys: [`${ItemTypes.metadataFolder}_${id}/metadata`],
      expandedKeys,
    });
  };

  onSelectMetadata = (metadata) => {
    const metadataClassName = metadata && metadata.name;
    if (!metadataClassName) {
      return;
    }
    let expandedKeys = this.state.expandedKeys;
    if (this.rootItems) {
      const item = getTreeItemByKey(`${ItemTypes.metadata}_${metadata.id}`, this.rootItems);
      if (item) {
        expandItem(item, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState(
      {
        isMetadata: true,
        isMetadataFolder: false,
        selectedMetadataClassEntity: [],
        metadataClassName,
        folderId: parseInt(metadata.id, 10),
        selectedKeys: [`${ItemTypes.metadata}_${metadata.id}`],
        expandedKeys,
      },
      () => {
        this.updateInitialSelection(false);
      },
    );
  };

  onSelectMetadataItems = (items) => {
    const selectedMetadata = (items || [])
      .map((o) => {
        if (typeof o === 'number') {
          return o;
        }
        if (typeof o === 'object' && o.rowKey) {
          return Number(o.rowKey.value);
        }
        return undefined;
      })
      .filter(Boolean);
    this.setState({selectedMetadata});
  };

  onSelectMetadataEntityItem = (item) => {
    const selectedMetadataClassEntity = item || [];
    this.setState({selectedMetadataClassEntity});
  };

  onSelectItem = (item, configuration) => {
    const {type, id} = item;
    switch (type) {
      case ItemTypes.folder:
        this.onSelectFolder(id);
        break;
      case ItemTypes.metadataFolder:
        this.onSelectMetadataFolder(id);
        break;
      case ItemTypes.metadata:
        this.onSelectMetadata(item);
        break;
    }
  };

  renderExpansionExpression = () => {
    if (this.props.hideExpansionExpression) {
      return null;
    }
    let filteredEntityFields = this.state.filteredEntityFields;
    const getType = (name, matadataEntity) => {
      const [currentField] = matadataEntity.fields.filter((field) => field.name === name);
      return currentField ? currentField.type : null;
    };

    const handleSearch = (value) => {
      this.setState({expansionExpression: value});
      if (!value || value.indexOf('this.') !== 0) {
        this.setState({
          filteredEntityFields: [],
        });
      } else {
        const parseValue = value.split('.');

        let currentRootEntity;
        if (this.state.isMetadata) {
          [currentRootEntity] = this.props.currentMetadataEntity.filter(
            (matadataEntity) =>
              `${matadataEntity.metadataClass.name}` === this.state.metadataClassName,
          );
        } else if (this.state.isMetadataFolder && this.state.selectedMetadataClassEntity.length) {
          const currentMetadataClassEntity = this.state.selectedMetadataClassEntity[0];
          [currentRootEntity] = this.props.currentMetadataEntity.filter(
            (matadataEntity) =>
              `${matadataEntity.metadataClass.name}` === currentMetadataClassEntity.name,
          );
        }

        for (let i = 1; i < parseValue.length - 1; i++) {
          const type = getType(parseValue[i], currentRootEntity);
          [currentRootEntity] = this.props.currentMetadataEntity.filter(
            (matadataEntity) => `${matadataEntity.metadataClass.name}` === type,
          );
          if (!type) return;
        }

        filteredEntityFields = (currentRootEntity ? currentRootEntity.fields : []).filter(
          (field) =>
            field.name.toLowerCase().indexOf(parseValue[parseValue.length - 1].toLowerCase()) >= 0,
        );

        this.setState({
          filteredEntityFields,
        });
      }
    };

    return (
      <Row style={{display: 'flex', paddingTop: 10}}>
        <div className={styles.expansionExpressionTitle}>Define expression</div>
        <AutoComplete
          style={{width: '100%'}}
          disabled={!this.isExpansionExpressionAvailable}
          value={this.state.expansionExpression}
          filterOption={false}
          onChange={handleSearch}
          onFocus={() => handleSearch(this.state.expansionExpression)}
        >
          {this.state.filteredEntityFields.map((field) => {
            let currentValue = field.name;
            if (this.state.expansionExpression) {
              const parseValue = this.state.expansionExpression.split('.');
              parseValue.pop();
              currentValue = parseValue.join('.') + '.' + field.name;
            }
            return (
              <AutoComplete.Option key={field.name} value={currentValue}>
                {field.name}
              </AutoComplete.Option>
            );
          })}
        </AutoComplete>
      </Row>
    );
  };

  onSearchChanged = async (e) => {
    await search(e, this.rootItems);
    const expandedKeys = getExpandedKeys(this.rootItems);
    this.setState({expandedKeys, search: e});
  };

  renderContent = () => {
    if (!this.props.tree.pending && this.props.tree.error) {
      return <Alert title="Error retrieving library" type="error" />;
    }
    let listingContent;
    if (this.state.isMetadataFolder) {
      listingContent = (
        <MetadataFolder
          id={this.state.folderId}
          onNavigate={this.onSelectItem}
          onSelectItem={this.onSelectMetadataEntityItem}
          selection={this.state.selectedMetadataClassEntity}
          selectionAvailable={!this.props.disableMetadataFolderSelection}
          hideUploadMetadataBtn
        />
      );
    } else if (this.state.isMetadata && this.state.metadataClassName) {
      listingContent = (
        <div style={{height: 450}}>
          <Metadata
            id={this.state.folderId}
            metadataClass={this.state.metadataClassName}
            class={this.state.metadataClassName}
            initialSelection={this.state.selectedMetadata}
            onSelectItems={this.onSelectMetadataItems}
            onNavigate={this.onSelectItem}
            hideUploadMetadataBtn
            readOnly={this.props.readOnly}
          />
        </div>
      );
    } else {
      listingContent = (
        <Folder
          id={this.state.folderId}
          treatAsRootId={this.props.initialFolderId}
          onSelectItem={this.onSelectItem}
          listingMode
          readOnly
          supportedTypes={[ItemTypes.metadataFolder, ItemTypes.metadata]}
        />
      );
    }
    return (
      <Splitter className={styles.browserSplitPane}>
        <Splitter.Panel min={200} defaultSize={200}>
          <div className={styles.browserTreePane}>
            <Row>
              <Input.Search onSearch={this.onSearchChanged} />
            </Row>
            <div className={styles.browserTreeContainer}>{this.generateTree()}</div>
          </div>
        </Splitter.Panel>
        <Splitter.Panel min={200}>{listingContent}</Splitter.Panel>
      </Splitter>
    );
  };

  render() {
    return (
      <Modal
        width="80%"
        title="Select metadata"
        closable={false}
        footer={
          <Row type="flex" justify="space-between">
            <Col>
              <Button onClick={() => this.onClearSelectionClicked()}>Clear selection</Button>
            </Col>
            <Col className={styles.buttonsContainer}>
              <Button onClick={() => this.onCancelClicked()}>Cancel</Button>
              <Button
                type="primary"
                disabled={!this.isSelectAvailable}
                onClick={() => this.onSubmitClicked()}
              >
                OK
                {this.isSelectAvailable &&
                  this.selectedItemsCount &&
                  ` (${this.selectedItemsCount})`}
              </Button>
            </Col>
          </Row>
        }
        open={this.props.visible}
        destroyOnHidden
      >
        <div className={styles.browserContent}>{this.renderContent()}</div>
        {this.renderExpansionExpression()}
      </Modal>
    );
  }

  getInitialBrowseState = () => ({
    isMetadataFolder: false,
    isMetadata: false,
    metadataClassName: null,
    selectedMetadata: [],
    selectedMetadataClassEntity: [],
    expansionExpression: '',
    filteredEntityFields: [],
  });

  updateInitialSelection = (navigate = true) => {
    if (this.props.selection && Object.keys(this.props.selection).length) {
      const {entitiesIds, folderId, metadataClassName} = this.props.selection;
      if (!metadataClassName) {
        return;
      }
      const selectionId = `${folderId}/metadata/${metadataClassName}`;
      const selectionFolderId = parseInt(selectionId, 10);
      const shouldNavigate =
        navigate &&
        (!this.state.isMetadata ||
          this.state.metadataClassName !== metadataClassName ||
          this.state.folderId !== selectionFolderId);
      if (shouldNavigate) {
        this.onSelectMetadata({
          id: selectionId,
          name: metadataClassName,
        });
      } else if (this.state.metadataClassName === metadataClassName) {
        this.setState({
          selectedMetadata: entitiesIds,
        });
      }
    }
  };

  updateState = ({resetBrowseState = false} = {}) => {
    const {initialFolderId, initialActiveFolderId} = this.props;
    const id = initialActiveFolderId || initialFolderId;
    const browseState = resetBrowseState ? this.getInitialBrowseState() : {};
    if (id) {
      let expandedKeys = this.state.expandedKeys;
      if (this.rootItems) {
        const item = getTreeItemByKey(`${ItemTypes.folder}_${id}`, this.rootItems);
        if (item) {
          expandItem(item, this.rootItems);
          expandedKeys = getExpandedKeys(this.rootItems);
        }
      }
      this.setState(
        {
          ...browseState,
          folderId: id,
          selectedKeys: [`${ItemTypes.folder}_${id}`],
          expandedKeys,
          search: null,
        },
        () => this.updateInitialSelection(true),
      );
    } else {
      this.setState(
        {
          ...browseState,
          folderId: null,
          selectedKeys: [`${ItemTypes.folder}_root`],
          expandedKeys: getExpandedKeys(this.rootItems),
          search: null,
        },
        () => this.updateInitialSelection(true),
      );
    }
  };

  componentDidMount() {
    this.updateState();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.initialFolderId !== this.props.initialFolderId) {
      this.props.tree.invalidateCache();
      this.rootItems = null;
    }
    const selectionChanged = !isSelectionEqual(prevProps.selection, this.props.selection);
    const visibilityChanged = prevProps.visible !== this.props.visible;
    const folderIdChanged = prevProps.initialFolderId !== this.props.initialFolderId;
    if (visibilityChanged && this.props.visible) {
      this.rootItems = null;
      this.setState({treeReady: false});
    }
    if (folderIdChanged || visibilityChanged || (this.props.visible && selectionChanged)) {
      this.updateState({
        resetBrowseState: visibilityChanged && this.props.visible,
      });
    } else if (!this.state.treeReady && this.rootItems && this.rootItems.length > 0) {
      this.setState(
        {
          treeReady: true,
        },
        () => {
          if (!this.state.isMetadata && !this.state.isMetadataFolder) {
            this.updateState();
          }
        },
      );
    }
  }

  componentWillUnmount() {
    this.props.tree.invalidateCache();
  }
}
