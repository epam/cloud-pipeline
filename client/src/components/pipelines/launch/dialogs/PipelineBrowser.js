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
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import connect from '../../../../utils/connect';
import localization from '../../../../utils/localization';
import PropTypes from 'prop-types';
import SplitPane from 'react-split-pane';
import {Modal, Button, Row, Col, Alert, Icon, Tree, Input} from 'antd';
import Folder from '../../browser/Folder';
import Pipeline from '../../browser/Pipeline';
import pipelinesLibrary from '../../../../models/folders/FolderLoadTree';
import LoadingView from '../../../special/LoadingView';
import {
  ItemTypes,
  generateTreeData,
  getTreeItemByKey,
  getExpandedKeys,
  expandItem,
  search,
  formatTreeItems
} from '../../model/treeStructureFunctions';

import styles from './Browser.css';
import HiddenObjects from '../../../../utils/hidden-objects';

@localization.localizedComponent
@connect({
  pipelinesLibrary
})
@inject('preferences')
@inject(() => ({
  library: pipelinesLibrary
}))
@HiddenObjects.injectTreeFilter
@observer
export default class PipelineBrowser extends localization.LocalizedReactComponent {
  static propTypes = {
    pipelineId: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    pipelineName: PropTypes.string,
    version: PropTypes.string,
    pipelineConfiguration: PropTypes.string,
    visible: PropTypes.bool,
    onSelect: PropTypes.func,
    onCancel: PropTypes.func,
    allowSelectLatestVersion: PropTypes.bool
  };

  rootItems = [];

  state = {
    folderId: null,
    pipelineId: null,
    selectedPipeline: null,
    expandedKeys: [],
    selectedKeys: [],
    treeReady: false,
    selectionChanged: false,
    search: null
  };

  onClearSelectionClicked = () => {
    this.setState({
      selectedPipeline: null,
      selectionChanged: true
    });
  };

  onCancelClicked = () => {
    if (this.props.onCancel) {
      this.props.onCancel();
      this.setState({
        selectedPipeline: null,
        selectionChanged: false
      });
    }
  };

  onSelectClicked = async () => {
    if (this.props.onSelect) {
      let result = await this.props.onSelect(this.state.selectedPipeline);
      if (result) {
        this.setState({
          selectedPipeline: null,
          selectionChanged: false
        });
      }
    }
  };

  renderItemTitle (item) {
    let icon;
    const style = {};
    switch (item.type) {
      case ItemTypes.pipeline: icon = 'fork'; break;
      case ItemTypes.versionedStorage:
        style.color = '#2796dd';
        icon = 'inbox';
        break;
      case ItemTypes.folder: icon = 'folder'; break;
      case ItemTypes.version: icon = 'tag'; break;
      case ItemTypes.storage:
        if (item.storageType && item.storageType.toLowerCase() !== 'nfs') {
          icon = 'inbox';
        } else {
          icon = 'hdd';
        }
        break;
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
        {icon && <Icon type={icon} style={style} />}{name}
      </span>
    );
  }

  generateTreeItems (items) {
    if (!items) {
      return [];
    }
    return formatTreeItems(items, {preferences: this.props.preferences})
      .map(item => {
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

  onSelect = (selectedKeys, {node}) => {
    const item = getTreeItemByKey(node.props.eventKey, this.rootItems);
    if (item.type === ItemTypes.pipeline) {
      this.onSelectPipeline(item);
    } else if (item.type === ItemTypes.folder) {
      this.onSelectFolder(item.id);
    }
  };

  @computed
  get libraryItems () {
    if (this.props.library.loaded) {
      return this.props.library.value;
    }
    return {};
  }

  generateTree () {
    if (!this.rootItems) {
      this.rootItems = generateTreeData(
        {...this.libraryItems},
        {
          types: [ItemTypes.pipeline],
          filter: this.props.hiddenObjectsTreeFilter()
        }
      );
    }
    return (
      <Tree
        className={styles.libraryTree}
        onSelect={this.onSelect}
        onExpand={this.onExpand}
        checkStrictly
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
      pipelineId: null,
      folderId: id,
      selectedKeys: [`${ItemTypes.folder}_${id}`],
      expandedKeys
    });
  };

  onSelectPipeline = (pipeline) => {
    const {id} = pipeline;
    let expandedKeys = this.state.expandedKeys;
    if (this.rootItems) {
      const item = getTreeItemByKey(`${ItemTypes.pipeline}_${id}`, this.rootItems);
      if (item) {
        expandItem(item, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState({
      pipelineId: id,
      folderId: null,
      selectedKeys: [`${ItemTypes.pipeline}_${id}`],
      expandedKeys
    });
  };

  onSelectItem = (item, configuration) => {
    const {type, id} = item;
    switch (type) {
      case ItemTypes.pipeline: this.onSelectPipeline(item); break;
      case ItemTypes.folder: this.onSelectFolder(id); break;
      case ItemTypes.version:
        this.setState({
          selectedPipeline: {
            id: this.state.pipelineId,
            version: item.name,
            configuration: configuration
          },
          selectionChanged: true
        });
        break;
    }
  };

  onSearchChanged = async (e) => {
    await search(e, this.rootItems);
    const expandedKeys = getExpandedKeys(this.rootItems);
    this.setState({expandedKeys, search: e});
  };

  render () {
    let content = <LoadingView />;
    if (!this.props.library.pending && this.props.library.error) {
      content = <Alert message="Error retrieving library" type="error" />;
    } else if (!this.props.library.pending) {
      let listingContent;
      const listingContainerStyle = {};
      const pane2Style = {
        overflowY: 'auto',
        overflowX: 'hidden'
      };
      if (this.state.pipelineId) {
        let selectedVersion, selectedConfiguration;
        if (
          this.state.selectedPipeline &&
          this.state.selectedPipeline.id === this.state.pipelineId
        ) {
          selectedVersion = this.state.selectedPipeline.version;
          selectedConfiguration = this.state.selectedPipeline.configuration;
        }
        listingContent = (
          <Pipeline
            id={this.state.pipelineId}
            selectedVersion={selectedVersion}
            selectedConfiguration={selectedConfiguration}
            onSelectItem={this.onSelectItem}
            listingMode
            configurationSelectionMode
            readOnly
            allowSelectLatestVersion={!!this.props.allowSelectLatestVersion}
          />
        );
      } else {
        listingContent = (
          <Folder
            id={/^root$/i.test(this.state.folderId) ? undefined : this.state.folderId}
            onSelectItem={this.onSelectItem}
            listingMode
            readOnly
            supportedTypes={[ItemTypes.folder, ItemTypes.pipeline]} />
        );
      }
      content = (
        <SplitPane
          split="vertical"
          minSize={200}
          pane1Style={{
            overflowY: 'auto',
            overflowX: 'hidden'
          }}
          pane2Style={pane2Style}
          resizerClassName="cp-split-panel-resizer"
          resizerStyle={{
            width: 3,
            margin: '0 5px',
            cursor: 'col-resize',
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
          <div style={listingContainerStyle}>
            {listingContent}
          </div>
        </SplitPane>
      );
    }

    return (
      <Modal
        width="80%"
        title={`Select ${this.localizedString('pipeline')}`}
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
                disabled={!this.state.selectionChanged}
                onClick={() => this.onSelectClicked()}>
                OK
              </Button>
            </Col>
          </Row>
        }
        visible={this.props.visible}>
        <Row style={{height: 450}}>
          {content}
        </Row>
      </Modal>
    );
  }

  updateState = () => {
    if (this.props.pipelineId && this.props.version) {
      let expandedKeys = this.state.expandedKeys;
      if (this.rootItems) {
        const item = getTreeItemByKey(
          `${ItemTypes.pipeline}_${this.props.pipelineId}`,
          this.rootItems
        );
        if (item) {
          expandItem(item, this.rootItems);
          expandedKeys = getExpandedKeys(this.rootItems);
        }
      }
      this.setState({
        folderId: null,
        pipelineId: this.props.pipelineId,
        selectedPipeline: {
          id: this.props.pipelineId,
          version: this.props.version,
          configuration: this.props.pipelineConfiguration
        },
        selectedKeys: [`${ItemTypes.pipeline}_${this.props.pipelineId}`],
        expandedKeys,
        selectionChanged: false
      });
    } else {
      this.setState({
        folderId: null,
        pipelineId: null,
        selectedPipeline: null,
        selectedKeys: [],
        expandedKeys: [],
        selectionChanged: false
      });
    }
  };

  componentDidMount () {
    this.updateState();
    this.props.library.fetch();
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.visible && nextProps.visible !== this.props.visible) {
      this.props.library.fetch();
      this.rootItems = null;
      this.setState({
        search: null
      });
    }
  }

  componentDidUpdate (prevProps) {
    if (prevProps.pipelineId !== this.props.pipelineId ||
      prevProps.version !== this.props.version ||
      prevProps.pipelineConfiguration !== this.props.pipelineConfiguration ||
      prevProps.visible !== this.props.visible
    ) {
      this.updateState();
    } else if (!this.state.treeReady && this.rootItems && this.rootItems.length > 0) {
      // eslint-disable-next-line
      this.setState({
        treeReady: true
      }, this.updateState);
    }
  }
}
