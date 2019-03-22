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
import FireCloudBrowser from '../../browser/FireCloudBrowser';
import pipelinesLibrary from '../../../../models/folders/FolderLoadTree';
import LoadingView from '../../../special/LoadingView';
import {
  ItemTypes,
  generateTreeData,
  getTreeItemByKey,
  getExpandedKeys,
  expandItem,
  search
} from '../../model/treeStructureFunctions';

import styles from './Browser.css';

@localization.localizedComponent
@connect({
  pipelinesLibrary
})
@inject('fireCloudMethods')
@inject(() => ({
  library: pipelinesLibrary
}))
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
    fireCloudMethod: PropTypes.string,
    fireCloudNamespace: PropTypes.string,
    fireCloudMethodSnapshot: PropTypes.string,
    fireCloudMethodConfiguration: PropTypes.string,
    fireCloudMethodConfigurationSnapshot: PropTypes.string,
    visible: PropTypes.bool,
    onSelect: PropTypes.func,
    onCancel: PropTypes.func
  };

  rootItems = [];

  state = {
    folderId: null,
    pipelineId: null,
    fireCloud: false,
    fireCloudMethod: this.props.fireCloudMethod || null,
    fireCloudNamespace: this.props.fireCloudNamespace || null,
    fireCloudMethodSnapshot: this.props.fireCloudMethodSnapshot || null,
    fireCloudMethodConfiguration: this.props.fireCloudMethodConfiguration || null,
    fireCloudMethodConfigurationSnapshot: this.props.fireCloudMethodConfigurationSnapshot || null,
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
      fireCloudMethod: null,
      fireCloudNamespace: null,
      fireCloudMethodSnapshot: null,
      fireCloudMethodConfiguration: null,
      fireCloudMethodConfigurationSnapshot: null,
      selectionChanged: true
    });
  };

  onCancelClicked = () => {
    if (this.props.onCancel) {
      this.props.onCancel();
      this.setState({
        selectedPipeline: null,
        fireCloudMethod: null,
        fireCloudNamespace: null,
        fireCloudMethodSnapshot: null,
        fireCloudMethodConfiguration: null,
        fireCloudMethodConfigurationSnapshot: null,
        selectionChanged: false
      });
    }
  };

  onSelectClicked = async () => {
    if (this.props.onSelect) {
      let result = false;
      if (!this.state.fireCloud) {
        result = await this.props.onSelect(this.state.selectedPipeline);
      } else {
        result = await this.props.onSelect({
          name: this.state.fireCloudMethod,
          namespace: this.state.fireCloudNamespace,
          snapshot: this.state.fireCloudMethodSnapshot,
          configuration: this.state.fireCloudMethodConfiguration,
          configurationSnapshot: this.state.fireCloudMethodConfigurationSnapshot
        }, true);
      }
      if (result) {
        this.setState({
          selectedPipeline: null,
          fireCloud: false,
          fireCloudNamespace: null,
          fireCloudMethod: null,
          fireCloudMethodSnapshot: null,
          fireCloudMethodConfiguration: null,
          fireCloudMethodConfigurationSnapshot: null,
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
      case ItemTypes.folder: icon = 'folder'; break;
      case ItemTypes.version: icon = 'tag'; break;
      case ItemTypes.storage:
        if (item.storageType && item.storageType.toLowerCase() !== 'nfs') {
          icon = 'inbox';
        } else {
          icon = 'hdd';
        }
        break;
      case ItemTypes.fireCloud:
        icon = 'cloud-o';
        style.color = '#2796dd';
        style.fontWeight = 'bold';
        break;
      case ItemTypes.fireCloudMethod:
        icon = 'fork';
        style.color = '#2796dd';
        style.fontWeight = 'bold';
        break;
    }
    let name = item.name;
    if (item.searchResult) {
      name = (
        <span>
          <span>{item.name.substring(0, item.searchResult.index)}</span>
          <span className={styles.searchResult}>
            {item.name.substring(item.searchResult.index, item.searchResult.index + item.searchResult.length)}
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

  onSelect = (selectedKeys, {node}) => {
    const item = getTreeItemByKey(node.props.eventKey, this.rootItems);
    if (item.type === ItemTypes.pipeline) {
      this.onSelectPipeline(item);
    } else if (item.type === ItemTypes.folder) {
      this.onSelectFolder(item.id);
    } else if (item.type === ItemTypes.fireCloud) {
      this.onSelectFireCloud();
    } else if (item.type === ItemTypes.fireCloudMethod) {
      this.onSelectFireCloudMethod(item.id, item.namespace);
    }
  };

  @computed
  get libraryItems () {
    if (this.props.library.loaded) {
      return this.props.library.value;
    }
    return {};
  }

  @computed
  get fireCloudItems () {
    if (this.props.fireCloudMethods.loaded) {
      return (this.props.fireCloudMethods.value || []).map(m => m);
    }
    return [];
  }

  generateTree () {
    if (!this.rootItems) {
      this.rootItems = generateTreeData(
        {...this.libraryItems, fireCloud: {methods: this.fireCloudItems}},
        false,
        null,
        [],
        [ItemTypes.pipeline, ItemTypes.fireCloud]
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

  onSelectFireCloud = () => {
    this.props.fireCloudMethods.fetch();
    let expandedKeys = this.state.expandedKeys;
    if (this.rootItems) {
      const item = getTreeItemByKey(ItemTypes.fireCloud, this.rootItems);
      if (item) {
        expandItem(item, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState({
      fireCloud: true,
      fireCloudNamespace: null,
      fireCloudMethod: null,
      fireCloudMethodSnapshot: null,
      fireCloudMethodConfiguration: null,
      fireCloudMethodConfigurationSnapshot: null,
      pipelineId: null,
      folderId: null,
      selectedKeys: [ItemTypes.fireCloud],
      expandedKeys
    });
  };

  onSelectFireCloudMethod = (id, namespace) => {
    this.setState({
      fireCloud: true,
      fireCloudNamespace: namespace,
      fireCloudMethod: id,
      fireCloudMethodSnapshot: null,
      fireCloudMethodConfiguration: null,
      fireCloudMethodConfigurationSnapshot: null,
      pipelineId: null,
      folderId: null
    });
  };

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
      fireCloud: false,
      fireCloudNamespace: null,
      fireCloudMethod: null,
      fireCloudMethodSnapshot: null,
      fireCloudMethodConfiguration: null,
      fireCloudMethodConfigurationSnapshot: null,
      pipelineId: null,
      folderId: id,
      selectedKeys: [`${ItemTypes.folder}_${id}`],
      expandedKeys
    });
  };

  onSelectPipeline = (pipeline) => {
    const {id, currentVersion} = pipeline;
    let expandedKeys = this.state.expandedKeys;
    if (this.rootItems) {
      const item = getTreeItemByKey(`${ItemTypes.pipeline}_${id}`, this.rootItems);
      if (item) {
        expandItem(item, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState({
      fireCloud: false,
      fireCloudNamespace: null,
      fireCloudMethod: null,
      fireCloudMethodSnapshot: null,
      fireCloudMethodConfiguration: null,
      fireCloudMethodConfigurationSnapshot: null,
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

  onFireCloudItemSelect = (fireCloudItem) => {
    this.setState({
      fireCloudNamespace: fireCloudItem.namespace,
      fireCloudMethod: fireCloudItem.name,
      fireCloudMethodSnapshot: fireCloudItem.snapshot,
      fireCloudMethodConfiguration: fireCloudItem.configuration,
      fireCloudMethodConfigurationSnapshot: fireCloudItem.configurationSnapshot,
      selectedPipeline: null,
      selectionChanged: true
    });
  };

  onNewFireCloudItemSelect = async (fireCloudItem) => {
    if (this.props.onSelect) {
      const result = await this.props.onSelect({
        name: fireCloudItem.name,
        namespace: fireCloudItem.namespace,
        snapshot: fireCloudItem.snapshot,
        configuration: null,
        configurationSnapshot: null
      }, true);
      if (result) {
        this.setState({
          selectedPipeline: null,
          fireCloud: false,
          fireCloudNamespace: null,
          fireCloudMethod: null,
          fireCloudMethodSnapshot: null,
          fireCloudMethodConfiguration: null,
          fireCloudMethodConfigurationSnapshot: null,
          selectionChanged: false
        });
      }
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
      if (this.state.fireCloud) {
        listingContainerStyle.width = '100%';
        listingContainerStyle.height = '100%';
        listingContainerStyle.display = 'flex';
        listingContainerStyle.flexDirection = 'column';
        pane2Style.overflowY = 'inherit';
        listingContent = (
          <FireCloudBrowser
            onFireCloudItemSelect={this.onFireCloudItemSelect}
            onNewFireCloudItemSelect={this.onNewFireCloudItemSelect}
            namespace={this.state.fireCloudNamespace}
            method={this.state.fireCloudMethod}
            snapshot={this.state.fireCloudMethodSnapshot}
            configuration={this.state.fireCloudMethodConfiguration}
            configurationSnapshot={this.state.fireCloudMethodConfigurationSnapshot}
          />
        );
      } else if (this.state.pipelineId) {
        let selectedVersion, selectedConfiguration;
        if (this.state.selectedPipeline && this.state.selectedPipeline.id === this.state.pipelineId) {
          selectedVersion = this.state.selectedPipeline.version;
          selectedConfiguration = this.state.selectedPipeline.configuration;
        }
        listingContent = (
          <Pipeline
            id={this.state.pipelineId}
            selectedVersion={selectedVersion}
            selectedConfiguration={selectedConfiguration}
            onSelectItem={this.onSelectItem}
            listingMode={true}
            configurationSelectionMode={true}
            readOnly={true} />
        );
      } else {
        listingContent = (
          <Folder
            id={this.state.folderId}
            onSelectItem={this.onSelectItem}
            listingMode={true}
            readOnly={true}
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
    if (this.props.pipelineId && this.props.version &&
      !this.props.fireCloudMethod && !this.props.fireCloudNamespace &&
      !this.props.fireCloudMethodSnapshot) {
      let expandedKeys = this.state.expandedKeys;
      if (this.rootItems) {
        const item = getTreeItemByKey(`${ItemTypes.pipeline}_${this.props.pipelineId}`, this.rootItems);
        if (item) {
          expandItem(item, this.rootItems);
          expandedKeys = getExpandedKeys(this.rootItems);
        }
      }
      this.setState({
        fireCloud: false,
        fireCloudNamespace: null,
        fireCloudMethod: null,
        fireCloudMethodSnapshot: null,
        fireCloudMethodConfiguration: null,
        fireCloudMethodConfigurationSnapshot: null,
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
    } else if (this.props.fireCloudMethod && this.props.fireCloudNamespace &&
      this.props.fireCloudMethodSnapshot) {
      this.setState({
        fireCloud: true,
        fireCloudNamespace: this.props.fireCloudNamespace,
        fireCloudMethod: this.props.fireCloudMethod,
        fireCloudMethodSnapshot: this.props.fireCloudMethodSnapshot,
        fireCloudMethodConfiguration: this.props.fireCloudMethodConfiguration,
        fireCloudMethodConfigurationSnapshot: this.props.fireCloudMethodConfigurationSnapshot,
        folderId: null,
        pipelineId: null,
        selectedPipeline: null,
        expandedKeys: [],
        selectedKeys: [ItemTypes.fireCloud],
        selectionChanged: false
      });
    } else {
      this.setState({
        fireCloud: false,
        fireCloudNamespace: null,
        fireCloudMethod: null,
        fireCloudMethodSnapshot: null,
        fireCloudMethodConfiguration: null,
        fireCloudMethodConfigurationSnapshot: null,
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
      prevProps.visible !== this.props.visible ||
      this.props.fireCloudMethod !== prevProps.fireCloudMethod ||
      this.props.fireCloudNamespace !== prevProps.fireCloudNamespace ||
      this.props.fireCloudMethodSnapshot !== prevProps.fireCloudMethodSnapshot ||
      this.props.fireCloudMethodConfiguration !== prevProps.fireCloudMethodConfiguration ||
      this.props.fireCloudMethodConfigurationSnapshot !== prevProps.fireCloudMethodConfigurationSnapshot
    ) {
      this.updateState();
    } else if (!this.state.treeReady && this.rootItems && this.rootItems.length > 0) {
      this.setState({
        treeReady: true
      }, this.updateState);
    }
  }

}
