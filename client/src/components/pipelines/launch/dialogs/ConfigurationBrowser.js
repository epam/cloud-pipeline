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
import {Alert, Button, Col, Icon, Modal, Row, Select, Tree} from 'antd';
import Folder from '../../browser/Folder';
import FolderLoad from '../../../../models/folders/FolderLoad';
import LoadingView from '../../../special/LoadingView';
import {
  expandItem,
  generateTreeData,
  getExpandedKeys,
  getTreeItemByKey,
  ItemTypes
} from '../../model/treeStructureFunctions';

import styles from './Browser.css';
import roleModel from '../../../../utils/roleModel';

@inject(({routing}, params) => ({
  tree: new FolderLoad(params.initialFolderId ? params.initialFolderId : null)
}))

@observer
export default class ConfigurationBrowser extends React.Component {

  static propTypes = {
    initialFolderId: PropTypes.number,
    visible: PropTypes.bool,
    onSelect: PropTypes.func,
    onCancel: PropTypes.func,
    metadataClassName: PropTypes.string,
    currentMetadataEntity: PropTypes.array
  };

  rootItems = [];

  state = {
    folderId: null,
    expandedKeys: [],
    selectedKeys: [],
    selectedConfiguration: null,
    treeReady: false,
    isMetadataFolder: false,
    isMetadata: false,
    expansionExpression: '',
    filteredEntityFields: []
  };

  onCancelClicked = () => {
    if (this.props.onCancel) {
      this.props.onCancel();
    }
  };

  onSubmitClicked = async () => {
    if (this.props.onSelect) {
      await this.props.onSelect(this.state.selectedConfiguration, this.state.expansionExpression);
    }
  };
  onExpand = (expandedKeys, {expanded, node}) => {
    const item = getTreeItemByKey(node.props.eventKey, this.rootItems);
    if (item) {
      expandItem(item, expanded);
    }
    this.setState({expandedKeys: getExpandedKeys(this.rootItems)});
  };
  onSelect = (selectedKeys, {node}) => {
    const item = getTreeItemByKey(node.props.eventKey, this.rootItems);
    if (item.type === ItemTypes.folder) {
      this.onSelectFolder(item.id);
    }
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
      folderId: id,
      selectedKeys: [`${ItemTypes.folder}_${id}`],
      expandedKeys,
      selectedConfiguration: null,
      expansionExpression: ''
    });
  };
  onSelectConfiguration = (item) => {
    this.setState({
      selectedConfiguration: item,
      expansionExpression: ''
    });
  };
  onSelectItem = (item) => {
    const {type, id} = item;
    switch (type) {
      case ItemTypes.folder: this.onSelectFolder(id); break;
      case ItemTypes.configuration: this.onSelectConfiguration(item); break;
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

        [currentRootEntity] =
          this.props.currentMetadataEntity.filter(matadataEntity =>
            `${matadataEntity.metadataClass.name}` === this.props.metadataClassName
          );

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
        expandedKeys
      });
    } else {
      this.setState({
        folderId: null,
        selectedKeys: [],
        expandedKeys: []
      });
    }
  };

  get isExpansionExpressionAvailable () {
    if (this.state.selectedConfiguration && this.state.selectedConfiguration.entries) {
      const [defaultRootEntity] =
        this.state.selectedConfiguration.entries.filter(entry => !!entry.rootEntityId);
      if (!defaultRootEntity) return false;
      const [selectedEntityConfiguration] =
        this.props.currentMetadataEntity.filter(matadataEntity =>
        matadataEntity.metadataClass.id === defaultRootEntity.rootEntityId
      );
      return selectedEntityConfiguration && selectedEntityConfiguration.metadataClass
        ? selectedEntityConfiguration.metadataClass.name !== this.props.metadataClassName
        : false;
    } else {
      return false;
    }
  }

  renderItemTitle (item) {
    let icon;
    switch (item.type) {
      case ItemTypes.folder: icon = 'folder'; break;
      case ItemTypes.configuration: icon = 'setting'; break;
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

  filterConfigurations = (item, type) => {
    if (type === ItemTypes.configuration) {
      return item.entries.filter(e => !!e.rootEntityId).length > 0;
    }
    return true;
  };

  generateTree () {
    if (!this.props.tree.pending && !this.props.tree.error) {
      this.rootItems = generateTreeData(
        this.props.tree.value,
        false,
        null,
        [],
        [ItemTypes.configuration],
        this.filterConfigurations
      );
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

  render () {
    let content = <LoadingView />;
    if (!this.props.tree.pending && this.props.tree.error) {
      content = <Alert message="Error retrieving configurations" type="error" />;
    } else if (!this.props.tree.pending) {
      content = (
        <SplitPane
          split="vertical"
          minSize={200}
          pane1Style={{
            overflowY: 'auto',
            overflowX: 'hidden'
          }}
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
          <div>
            {this.generateTree()}
          </div>
          <Row style={{height: '100%'}}>
            <Folder
              id={this.state.folderId}
              treatAsRootId={this.props.initialFolderId}
              onSelectItem={this.onSelectItem}
              listingMode={true}
              showConfigurationPreview={true}
              readOnly={true}
              highlightByClick={true}
              supportedTypes={[ItemTypes.configuration]}
              filterItems={this.filterConfigurations} />
          </Row>
        </SplitPane>
      );
    }
    return (
      <Modal
        width="80%"
        title={'Select configuration'}
        closable={false}
        footer={
          <Row type="flex" justify="space-between">
            <Col />
            <Col className={styles.buttonsContainer}>
              <Button
                onClick={() => this.onCancelClicked()}>Cancel</Button>
              <Button
                type="primary"
                disabled={
                  !this.state.selectedConfiguration ||
                  !roleModel.executeAllowed(this.state.selectedConfiguration)
                }
                onClick={() => {
                  Modal.confirm({
                    title: 'Run configuration?',
                    content: '',
                    onOk: async () => {
                      await this.onSubmitClicked();
                    },
                    okText: 'RUN',
                    cancelText: 'CANCEL'
                  });
                }}>
                OK
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

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.initialFolderId !== this.props.initialFolderId ||
      prevProps.visible !== this.props.visible) {
      this.updateState();
    } else if (!this.state.treeReady && this.rootItems.length > 0) {
      this.setState({
        treeReady: true
      }, this.updateState);
    }
  }

}
