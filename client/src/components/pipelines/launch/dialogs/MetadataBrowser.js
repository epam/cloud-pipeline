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
import SplitPane from 'react-split-pane';
import {Alert, Button, Col, Icon, Input, Modal, Row, Select, Tree} from 'antd';
import Folder from '../../browser/Folder';
import Metadata from '../../browser/Metadata';
import MetadataFolder from '../../browser/MetadataFolder';
import FolderLoad from '../../../../models/folders/FolderLoad';
import LoadingView from '../../../special/LoadingView';
import {
  expandItem,
  generateTreeData,
  getExpandedKeys,
  getTreeItemByKey,
  ItemTypes,
  search
} from '../../model/treeStructureFunctions';

import styles from './Browser.css';

@inject(({routing}, params) => ({
  tree: new FolderLoad(params.initialFolderId ? params.initialFolderId : null)
}))
@observer
export default class MetadataBrowser extends React.Component {

  static propTypes = {
    initialFolderId: PropTypes.number,
    visible: PropTypes.bool,
    onSelect: PropTypes.func,
    onCancel: PropTypes.func,
    rootEntityId: PropTypes.string,
    currentMetadataEntity: PropTypes.array,
    readOnly: PropTypes.bool
  };

  rootItems = null;

  state = {
    folderId: null,
    expandedKeys: [],
    selectedKeys: [],
    selectedMetadata: [],
    selectedMetadataClassEntity: [],
    treeReady: false,
    isMetadataFolder: false,
    isMetadata: false,
    metadataClassName: null,
    expansionExpression: '',
    filteredEntityFields: [],
    search: null
  };

  onClearSelectionClicked = () => {
    this.setState({
      selectedMetadata: [],
      selectedMetadataClassEntity: []
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
      filteredEntityFields: []
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
      const entitiesIds = this.state.selectedMetadata.map(metadata => metadata.rowKey.value);
      this.props.onSelect(entitiesIds, selectedMetadataClassEntity, this.state.expansionExpression, folderId);
    }
  };

  get isSelectAvailable () {
    return this.state.selectedMetadata.length > 0 ||
      this.state.selectedMetadataClassEntity.length > 0;
  }

  get selectedItemsCount () {
    if (this.state.selectedMetadata.length > 0) {
      return this.state.selectedMetadata.length;
    } else if (this.state.selectedMetadataClassEntity.length > 0) {
      return this.state.selectedMetadataClassEntity.length;
    }
    return null;
  }

  get isExpansionExpressionAvailable () {
    let currentRootEntity;
    if (this.state.isMetadata) {
      [currentRootEntity] =
        this.props.currentMetadataEntity.filter(matadataEntity =>
          `${matadataEntity.metadataClass.name}` === this.state.metadataClassName
        );
    } else if (this.state.isMetadataFolder && this.state.selectedMetadataClassEntity.length) {
      const currentMetadataClassEntity = this.state.selectedMetadataClassEntity[0];
      [currentRootEntity] =
        this.props.currentMetadataEntity.filter(matadataEntity =>
          `${matadataEntity.metadataClass.name}` === currentMetadataClassEntity.name
        );
    }
    return currentRootEntity
      ? `${currentRootEntity.metadataClass.id}` !== this.props.rootEntityId : false;
  }

  renderItemTitle (item) {
    let icon;
    switch (item.type) {
      case ItemTypes.folder: icon = 'folder'; break;
      case ItemTypes.metadata: icon = 'appstore-o'; break;
      case ItemTypes.metadataFolder: icon = 'appstore-o'; break;
    }
    let name = item.name;
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
    return (
      <span
        id={`pipelines-library-tree-node-${item.key}-name`}
        className={styles.treeItemTitle}>
        {icon && <Icon type={icon} />}{name}
      </span>
    );
  }

  generateTreeItems (items) {
    if (!items) {
      return [];
    }
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

  onExpand = (expandedKeys, {expanded, node}) => {
    const item = getTreeItemByKey(node.props.eventKey, this.rootItems);
    if (item) {
      expandItem(item, expanded);
    }
    this.setState({expandedKeys: getExpandedKeys(this.rootItems)});
  };

  onSelect = (selectedKeys, node) => {
    const item = getTreeItemByKey(node.node.props.eventKey, this.rootItems);
    if (item.type === ItemTypes.metadataFolder) {
      this.onSelectMetadataFolder(item.id);
    } else if (item.type === ItemTypes.metadata) {
      this.onSelectMetadata(item);
    } else {
      this.onSelectFolder(item.id);
    }
  };

  generateTree () {
    if (!this.props.tree.pending && !this.props.tree.error && !this.rootItems) {
      const folder = {
        id: this.props.tree.value.id,
        key: `${ItemTypes.folder}_${this.props.tree.value.id}`,
        name: this.props.tree.value.name,
        type: ItemTypes.folder,
        parentId: null,
        parent: null,
        createdDate: this.props.tree.value.createdDate,
        mask: this.props.tree.value.mask
      };
      folder.children = generateTreeData(
        this.props.tree.value,
        false,
        folder,
        [],
        [ItemTypes.metadata]
      );
      folder.isLeaf = folder.children.length === 0;
      folder.expanded = true;

      this.rootItems = [folder];
    }
    return (
      <Tree
        className={styles.libraryTree}
        onSelect={this.onSelect}
        onExpand={this.onExpand}
        checkStrictly={true}
        expandedKeys={this.state.expandedKeys}
        selectedKeys={this.state.selectedKeys} >
        {this.generateTreeItems(this.rootItems)}
      </Tree>
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
      expandedKeys
    });
  };

  onSelectMetadataFolder = (id) => {
    let expandedKeys = this.state.expandedKeys;
    const intId = parseInt(id, 10);
    if (this.rootItems) {
      const item = getTreeItemByKey(`${ItemTypes.metadataFolder}_${id}`, this.rootItems);
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
      selectedKeys: [`${ItemTypes.metadataFolder}_${id}`],
      expandedKeys
    });
  };

  onSelectMetadata = (metadata) => {
    let expandedKeys = this.state.expandedKeys;
    if (this.rootItems) {
      const item = getTreeItemByKey(`${ItemTypes.metadata}_${metadata.id}`, this.rootItems);
      if (item) {
        expandItem(item, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState({
      isMetadata: true,
      isMetadataFolder: false,
      selectedMetadataClassEntity: [],
      metadataClassName: metadata.name,
      folderId: parseInt(metadata.id, 10),
      selectedKeys: [`${ItemTypes.metadata}_${metadata.id}`],
      expandedKeys
    });
  };

  onSelectMetadataItems = (items) => {
    const selectedMetadata = items || [];
    this.setState({selectedMetadata});
  };

  onSelectMetadataEntityItem = (item) => {
    const selectedMetadataClassEntity = item || [];
    this.setState({selectedMetadataClassEntity});
  };

  onSelectItem = (item, configuration) => {
    const {type, id} = item;
    switch (type) {
      case ItemTypes.folder: this.onSelectFolder(id); break;
      case ItemTypes.metadataFolder: this.onSelectMetadataFolder(id); break;
      case ItemTypes.metadata: this.onSelectMetadata(item); break;
    }
  };

  renderExpansionExpression = () => {
    let filteredEntityFields = this.state.filteredEntityFields;
    const getType = (name, matadataEntity) => {
      const [currentField] = matadataEntity.fields.filter(field => field.name === name);
      return currentField ? currentField.type : null;
    };

    const handleSearch = (value) => {
      this.setState({expansionExpression: value});
      if (!value || value.indexOf('this.') !== 0) {
        this.setState({
          filteredEntityFields: []
        });
      } else {
        const parseValue = value.split('.');

        let currentRootEntity;
        if (this.state.isMetadata) {
          [currentRootEntity] =
            this.props.currentMetadataEntity.filter(matadataEntity =>
              `${matadataEntity.metadataClass.name}` === this.state.metadataClassName
            );
        } else if (this.state.isMetadataFolder && this.state.selectedMetadataClassEntity.length) {
          const currentMetadataClassEntity = this.state.selectedMetadataClassEntity[0];
          [currentRootEntity] =
            this.props.currentMetadataEntity.filter(matadataEntity =>
              `${matadataEntity.metadataClass.name}` === currentMetadataClassEntity.name
            );
        }

        for (let i = 1; i < parseValue.length - 1; i++) {
          const type = getType(parseValue[i], currentRootEntity);
          [currentRootEntity] =
            this.props.currentMetadataEntity.filter(matadataEntity =>
              `${matadataEntity.metadataClass.name}` === type
            );
          if (!type) return;
        }

        filteredEntityFields =
          (currentRootEntity ? currentRootEntity.fields : [])
            .filter(field =>
              field.name.toLowerCase()
                .indexOf(parseValue[parseValue.length - 1].toLowerCase()) >= 0);

        this.setState({
          filteredEntityFields: filteredEntityFields
        });
      }
    };

    return (
      <Select
        style={{width: '100%'}}
        disabled={!this.isExpansionExpressionAvailable}
        value={this.state.expansionExpression}
        mode="combobox"
        filterOption={false}
        onChange={handleSearch}
        onFocus={() => {
          handleSearch(this.state.expansionExpression);
        }
        }
      >
        {
          this.state.filteredEntityFields.map(field => {
            let currentValue = field.name;
            if (this.state.expansionExpression) {
              const parseValue = this.state.expansionExpression.split('.');
              parseValue.pop();
              currentValue = parseValue.join('.') + '.' + field.name;
            }
            return (
              <Select.Option
                key={field.name}
                value={currentValue}>
                {field.name}
              </Select.Option>);
          })
        }
      </Select>
    );
  };

  onSearchChanged = async (e) => {
    await search(e, this.rootItems);
    const expandedKeys = getExpandedKeys(this.rootItems);
    this.setState({expandedKeys, search: e});
  };

  render () {
    let content = <LoadingView />;
    if (!this.props.tree.pending && this.props.tree.error) {
      content = <Alert message="Error retrieving library" type="error" />;
    } else if (!this.props.tree.pending) {
      let listingContent;
      if (this.state.isMetadataFolder) {
        listingContent = (
          <MetadataFolder
            id={this.state.folderId}
            onNavigate={this.onSelectItem}
            onSelectItem={this.onSelectMetadataEntityItem}
            initialSelection={this.state.selectedMetadataClassEntity}
            selectionAvailable={true}
            hideUploadMetadataBtn={true}
          />
        );
      } else if (this.state.isMetadata && this.state.metadataClassName) {
        listingContent = (
          <div style={{height: 450}}>
            <Metadata
              id={this.state.folderId}
              class={this.state.metadataClassName}
              initialSelection={this.state.selectedMetadata}
              onSelectItems={this.onSelectMetadataItems}
              hideUploadMetadataBtn={true}
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
            listingMode={true}
            readOnly={true}
            supportedTypes={[ItemTypes.metadataFolder, ItemTypes.metadata]} />
        );
      }
      content = (
        <SplitPane
          split="vertical"
          minSize={200}
          pane2Style={{
            overflowY: 'auto',
            overflowX: 'hidden'
          }}
          resizerStyle={{
            width: 3,
            margin: '0 5px',
            cursor: 'col-resize',
            backgroundColor: '#efefef',
            boxSizing: 'border-box',
            backgroundClip: 'padding',
            zIndex: 1
          }}>
          <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
            <Row>
              <Input.Search onSearch={this.onSearchChanged} />
            </Row>
            <div style={{flex: 1, overflowY: 'auto', overflowX: 'hidden'}}>
              {this.generateTree()}
            </div>
          </div>
          <div>
            {listingContent}
          </div>
        </SplitPane>
      );
    }

    return (
      <Modal
        width="80%"
        title="Select metadata"
        closable={false}
        footer={
          <Row type="flex" justify="space-between">
            <Col>
              <Button
                onClick={() => this.onClearSelectionClicked()}>Clear selection</Button>
            </Col>
            <Col className={styles.buttonsContainer}>
              <Button
                onClick={() => this.onCancelClicked()}>Cancel</Button>
              <Button
                type="primary"
                disabled={!this.isSelectAvailable}
                onClick={() => this.onSubmitClicked()}>
                OK
                {
                  this.isSelectAvailable && this.selectedItemsCount &&
                  ` (${this.selectedItemsCount})`
                }
              </Button>
            </Col>
          </Row>
        }
        visible={this.props.visible}>
        <Row style={{height: 450}}>
          {content}
        </Row>
        <Row style={{display: 'flex', paddingTop: 10}}>
          <div className={styles.expansionExpressionTitle}>Define expression</div>
          {this.renderExpansionExpression()}
        </Row>
      </Modal>
    );
  }

  updateState = () => {
    if (this.props.initialFolderId) {
      let expandedKeys = this.state.expandedKeys;
      if (this.rootItems) {
        const item = getTreeItemByKey(
          `${ItemTypes.folder}_${this.props.initialFolderId}`,
          this.rootItems
        );
        if (item) {
          expandItem(item, this.rootItems);
          expandedKeys = getExpandedKeys(this.rootItems);
        }
      }
      this.setState({
        folderId: this.props.initialFolderId,
        selectedKeys: [`${ItemTypes.folder}_${this.props.initialFolderId}`],
        expandedKeys,
        search: null
      });
    } else {
      this.setState({
        folderId: null,
        selectedKeys: [],
        expandedKeys: [],
        search: null
      });
    }
  };

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.initialFolderId !== this.props.initialFolderId ||
      prevProps.visible !== this.props.visible) {
      this.updateState();
    } else if (!this.state.treeReady && this.rootItems && this.rootItems.length > 0) {
      this.setState({
        treeReady: true
      }, this.updateState);
    }
  }

}
