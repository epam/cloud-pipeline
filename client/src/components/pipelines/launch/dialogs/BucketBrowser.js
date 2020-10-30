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
import connect from '../../../../utils/connect';
import {computed, observable} from 'mobx';
import PropTypes from 'prop-types';
import SplitPane from 'react-split-pane';
import {Alert, Button, Checkbox, Col, Icon, Input, Modal, Row, Table, Tree} from 'antd';
import dataStorages from '../../../../models/dataStorage/DataStorages';
import DataStorageRequest from '../../../../models/dataStorage/DataStoragePage';
import DTSRequest from '../../../../models/dts/DTSItemsPage';
import pipelinesLibrary from '../../../../models/folders/FolderLoadTree';
import LoadingView from '../../../special/LoadingView';
import AWSRegionTag from '../../../special/AWSRegionTag';
import displayDate from '../../../../utils/displayDate';
import displaySize from '../../../../utils/displaySize';
import roleModel from '../../../../utils/roleModel';
import {
  expandItem,
  generateTreeData,
  getExpandedKeys,
  getTreeItemByKey,
  ItemTypes,
  search
} from '../../model/treeStructureFunctions';

import styles from './Browser.css';

const PAGE_SIZE = 40;
const DTS_ITEM_TYPE = 'DTS';
const DTS_ROOT_ITEM_TYPE = 'DTS-ROOT';

const S3_BUCKET_TYPE = 'S3';
const NFS_BUCKET_TYPE = 'NFS';
const AZ_BUCKET_TYPE = 'AZ';
const GS_BUCKET_TYPE = 'GS';

@connect({
  pipelinesLibrary
})
@inject('dtsList')
@inject(() => ({
  storages: dataStorages,
  library: pipelinesLibrary
}))
@observer
export default class BucketBrowser extends React.Component {

  static propTypes = {
    path: PropTypes.string,
    visible: PropTypes.bool,
    onSelect: PropTypes.func,
    onCancel: PropTypes.func,
    multiple: PropTypes.bool,
    showOnlyFolder: PropTypes.bool,
    checkWritePermissions: PropTypes.bool,
    bucketTypes: PropTypes.arrayOf(
      PropTypes.oneOf([
        AZ_BUCKET_TYPE,
        S3_BUCKET_TYPE,
        GS_BUCKET_TYPE,
        NFS_BUCKET_TYPE,
        DTS_ITEM_TYPE
      ])
    )
  };

  @observable
  storage = null;
  rootItems = [];

  state = {
    bucket: null,
    path: null,
    selectedItems: [],
    expandedKeys: [],
    selectedKeys: [],
    currentPage: 0,
    pageMarkers: [null],
    pagePerformed: false,
    search: null
  };

  tableData = [];

  get storageIsFetching () {
    if (this.storage) {
      return this.storage.pending;
    }
    return false;
  }

  getRoot = (path) => {
    if (!path) {
      return '';
    }
    const pathComponents = path.split('://');
    let pathCorrected = path;
    if (pathComponents.length > 1) {
      pathCorrected = pathComponents[1];
    }
    return pathCorrected.split('/')[0];
  };

  getBucketByPath = (path) => {
    if (!this.props.storages.loaded || !this.props.dtsList.loaded) {
      return null;
    }
    const pathCorrected = this.getRoot(path);
    const storages = this.props.storages.value;
    for (let i = 0; i < storages.length; i++) {
      const storage = storages[i];
      const storagePath = this.getRoot(storage.path);
      if (pathCorrected.toLowerCase() === storagePath.toLowerCase()) {
        return storage;
      }
    }
    // check if DTS
    if (path) {
      for (let i = 0; i < (this.props.dtsList.value || []).length; i++) {
        const dts = this.props.dtsList.value[i];
        for (let j = 0; j < (dts.prefixes || []).length; j++) {
          const prefix = `${dts.prefixes[j]}/`.replace(/\/\//g, '/').toLowerCase();
          if (path.toLowerCase().indexOf(prefix) === 0) {
            return {
              ...dts,
              type: DTS_ROOT_ITEM_TYPE,
              prefix: dts.prefixes[j]
            };
          }
        }
      }
    }
    return null;
  };

  labelsRenderer = (labels) => {
    const labelsList = [];
    for (let key in labels) {
      if (labels.hasOwnProperty(key)) {
        labelsList.push({
          key: key,
          value: labels[key]
        });
      }
    }
    return labelsList.map(l => (<span className={styles.label} key={l.key}>{l.value}</span>));
  };

  canGoToParent () {
    if (this.storage) {
      return this.storage.path;
    }
    return this.state.path;
  }

  removeProtocol (path) {
    if (!path) {
      return path;
    }
    const parts = path.split('://');
    if (parts.length === 2) {
      return parts[1];
    }
    return path;
  }

  fixPath (path) {
    if (!path) {
      return path;
    }
    if (path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    path = this.removeProtocol(path);
    const parts = path.split('/');
    path = parts.length > 1 ? '' : undefined;
    for (let i = 1; i < parts.length; i++) {
      if (i === 1) {
        path += `${parts[i]}`;
      } else {
        path += `/${parts[i]}`;
      }
    }
    return path;
  }

  parentDirectory (path) {
    if (path) {
      if (path.endsWith('/')) {
        path = path.substring(0, path.length - 1);
      }
      const parts = path.split('/');
      if (parts.length > 1) {
        parts.pop();
        return parts.join('/');
      }
    }
    return undefined;
  }

  getItemFullPath = (item) => {
    if (this.state.bucket && (
        this.state.bucket.type === ItemTypes.storage ||
        this.state.bucket.type === S3_BUCKET_TYPE ||
        this.state.bucket.type === AZ_BUCKET_TYPE ||
        this.state.bucket.type === GS_BUCKET_TYPE ||
        this.state.bucket.type === NFS_BUCKET_TYPE
      )) {
      const type = this.state.bucket.storageType || this.state.bucket.type;
      if (type === 'NFS') {
        const storagePath = this.state.bucket.path.replace(':', '');
        const mountPoint = this.state.bucket.mountPoint
          ? this.state.bucket.mountPoint.endsWith('/')
            ? this.state.bucket.mountPoint.slice(0, -1)
            : this.state.bucket.mountPoint
          : null;
        return mountPoint
          ? `${mountPoint}/${item.path}`
          : `/cloud-data/${storagePath}/${item.path}`;
      }
      return `${type.toLowerCase()}://${this.state.bucket.path}/${item.path}`;
    } else if (this.state.bucket && this.state.bucket.type === DTS_ROOT_ITEM_TYPE) {
      return item.fullPath || '';
    }
    return item.path || '';
  };

  itemIsSelected = (item) => {
    if (this.state.selectedItems && this.state.selectedItems.length > 0) {
      if (this.props.multiple) {
        const filteredSelectedItems =
          this.state.selectedItems.filter(selectedItem =>
              selectedItem.name.trim().toLowerCase() === this.getItemFullPath(item).toLowerCase()
          );
        let isSelected = false;

        filteredSelectedItems.forEach(selectedItem => {
          if (selectedItem.type) {
            isSelected = isSelected || selectedItem.type === item.type;
          } else {
            const filteredData = this.tableData.filter(data =>
              this.getItemFullPath(data).toLowerCase() === this.getItemFullPath(item).toLowerCase()
            );
            if (filteredData.length > 1) {
              isSelected = isSelected || item.type.toLowerCase() === 'folder';
            } else {
              isSelected = true;
            }
          }
        });

        return isSelected;
      } else {
        return this.getItemFullPath(item).toLowerCase() ===
          (this.state.selectedItems[0].name || '').toLowerCase();
      }
    }
    return false;
  };

  selectItem = (event, item) => {
    event.stopPropagation();
    if (this.props.multiple) {
      const itemFullPath = this.getItemFullPath(item);
      if (this.state.selectedItems && this.state.selectedItems.length > 0) {
        const filteredData = this.tableData.filter(data =>
          this.getItemFullPath(data).toLowerCase() === itemFullPath.toLowerCase()
        );
        const index = this.state.selectedItems.findIndex((selectedItem) => {
          if (selectedItem.name.trim().toLowerCase() === itemFullPath.toLowerCase()) {
            if (selectedItem.type) {
              return selectedItem.type === item.type;
            } else {
              return filteredData.length > 1 ? item.type.toLowerCase() === 'folder' : true;
            }
          }
        });
        let selectedItems = this.state.selectedItems;
        if (index >= 0) {
          selectedItems.splice(index, 1);
        } else {
          selectedItems.push({name: itemFullPath, type: item.type});
        }
        this.setState({selectedItems: selectedItems});
      } else {
        this.setState({selectedItems: [{name: itemFullPath, type: item.type}]});
      }
    } else {
      if (this.itemIsSelected(item)) {
        this.setState({selectedItems: []});
      } else {
        this.setState({selectedItems: [{name: this.getItemFullPath(item), type: item.type}]});
      }
    }
  };

  getStorageItemsTable = () => {
    const columns = [
      {
        key: 'selection',
        title: '',
        className: styles.checkboxCell,
        render: (item) => {
          if (item.selectable) {
            return (
              <Checkbox
                disabled={this.props.checkWritePermissions && item.readOnly && !this.itemIsSelected(item)}
                checked={this.itemIsSelected(item)}
                onChange={(e) => this.selectItem(e, item)} />
            );
          } else {
            return <span />;
          }
        }
      },
      {
        dataIndex: 'type',
        key: 'type',
        title: '',
        className: styles.itemTypeCell,
        render: (text, item) => <Icon className={styles.itemType} type={item.type.toLowerCase()} />,
        onCellClick: (item) => this.didSelectDataStorageItem(item)
      },
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        className: styles.selectableCell,
        onCellClick: (item) => this.didSelectDataStorageItem(item)
      },
      {
        dataIndex: 'size',
        key: 'size',
        title: 'Size',
        render: size => displaySize(size),
        className: styles.selectableCell,
        onCellClick: (item) => this.didSelectDataStorageItem(item)
      },
      {
        dataIndex: 'changed',
        key: 'changed',
        title: 'Date changed',
        className: styles.selectableCell,
        render: (date) => date ? displayDate(date) : '',
        onCellClick: (item) => this.didSelectDataStorageItem(item)
      },
      {
        dataIndex: 'labels',
        key: 'labels',
        title: '',
        className: styles.selectableCell,
        render: this.labelsRenderer,
        onCellClick: (item) => this.didSelectDataStorageItem(item)
      }
    ];

    const getList = () => {
      const items = [];
      if (this.canGoToParent()) {
        items.push({
          name: '..',
          path: this.parentDirectory(this.state.path),
          type: 'folder',
          selectable: false
        });
      }
      let results = [];
      if (this.storage && this.storage.loaded) {
        results = this.storage.value.results || [];
      }
      items.push(...results.map(i => {
        if (this.state.bucket.type === DTS_ROOT_ITEM_TYPE) {
          const path = this.storage.path ? `${this.storage.path}/${i.path}`.replace(/\/\//g, '/') : i.path;
          return {
            ...i,
            selectable: true,
            fullPath: `${this.state.bucket.prefix}/${path}`.replace(/\/\//g, '/'),
            path,
            readOnly: this.props.checkWritePermissions && !roleModel.writeAllowed({mask: i.permission})
          };
        } else {
          return {
            ...i,
            selectable: true,
            readOnly: false
          };
        }
      }));
      return items;
    };

    this.tableData = this.storageIsFetching ? this.tableData : getList();

    return {
      columns,
      data:
        this.props.showOnlyFolder ? this.tableData.filter(r => r.type.toLowerCase() === 'folder') : this.tableData
    };
  };

  didSelectDataStorageItem = (item) => {
    if (item.type.toLowerCase() === 'folder') {
      this.setState({
        path: decodeURIComponent(item.path || ''),
        pageMarkers: [null],
        pagePerformed: false,
        currentPage: 0
      });
    }
  };

  bucketChanged = (key) => {
    let expandedKeys = this.state.expandedKeys;
    let bucket;
    if (this.rootItems) {
      bucket = getTreeItemByKey(key, this.rootItems);
      if (bucket) {
        expandItem(bucket, this.rootItems);
        expandedKeys = getExpandedKeys(this.rootItems);
      }
    }
    this.setState({
      bucket: bucket,
      expandedKeys,
      selectedKeys: [key],
      path: null
    });
  };

  onClearSelectionClicked = () => {
    this.setState({selectedItems: []});
  };

  onCancelClicked = () => {
    if (this.props.onCancel) {
      this.props.onCancel();
      this.setState({selectedItems: []});
    }
  };

  onSelectClicked = () => {
    if (this.props.onSelect) {
      this.props.onSelect(this.state.selectedItems.map(item => item.name).join(', '));
      this.setState({selectedItems: []});
    }
  };

  renderItemTitle (item) {
    let icon;
    let subTitle;
    switch (item.type) {
      case DTS_ITEM_TYPE: icon = 'desktop'; break;
      case DTS_ROOT_ITEM_TYPE: icon = 'inbox'; break;
      case ItemTypes.pipeline: icon = 'fork'; break;
      case ItemTypes.folder: icon = 'folder'; break;
      case ItemTypes.version: icon = 'tag'; break;
      case ItemTypes.storage:
        if (item.storageType && item.storageType.toLowerCase() !== 'nfs') {
          icon = 'inbox';
        } else {
          icon = 'hdd';
        }
        subTitle = <AWSRegionTag regionId={item.regionId} />;
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
        {icon && <Icon type={icon} />}<span className="storage-name">{name}</span>{subTitle}
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
    if (item.type === ItemTypes.storage || item.type === DTS_ROOT_ITEM_TYPE) {
      if (item.type === ItemTypes.storage) {
        this.bucketChanged(`storage_${item.id}`);
      } else {
        this.bucketChanged(`${DTS_ROOT_ITEM_TYPE}_${item.id}_${item.path}`);
      }
    } else if (item.type === ItemTypes.folder || item.type === DTS_ITEM_TYPE) {
      expandItem(item, true);
      this.setState({expandedKeys: getExpandedKeys(this.rootItems)});
    }
  };

  postprocessTree (items) {
    const result = [];
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.type === ItemTypes.storage) {
        result.push(item);
      } else if (item.type === ItemTypes.folder && item.children && item.children.length) {
        item.children = this.postprocessTree(item.children);
        if (item.children.length) {
          result.push(item);
        }
      }
    }
    return result;
  }

  generateTree () {
    if (this.props.library.loaded &&
      this.props.dtsList.loaded &&
      !this.rootItems) {
      this.rootItems = [
        ...(this.props.dtsList.value || [])
          .filter(r => {
            if (!this.props.bucketTypes || this.props.bucketTypes.length === 0) {
              return true;
            }
            return this.props.bucketTypes.indexOf(DTS_ITEM_TYPE) >= 0;
          })
          .map(dtsItem => {
            const parent = {
              ...dtsItem,
              type: DTS_ITEM_TYPE,
              key: `${DTS_ITEM_TYPE}_${dtsItem.id}`,
              isLeaf: false,
              expanded: false
            };
            parent.children = (dtsItem.prefixes || []).map(p => {
              const name = p.split('/').filter(s => !!s).slice(-1).pop();
              return {
                ...dtsItem,
                type: DTS_ROOT_ITEM_TYPE,
                key: `${DTS_ROOT_ITEM_TYPE}_${dtsItem.id}_${p}`,
                isLeaf: true,
                path: p,
                prefix: p,
                name,
                parent,
                expanded: false
              };
            });
            return parent;
          }),
        ...this.postprocessTree(
          generateTreeData(
            this.props.library.value,
            false,
            null,
            [],
            [ItemTypes.storage],
            (item, type) => {
              if (!this.props.bucketTypes || this.props.bucketTypes.length === 0 || type !== ItemTypes.storage) {
                return true;
              }
              return this.props.bucketTypes.indexOf(item.type) >= 0;
            }
          )
        )];
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

  onSearchChanged = async (e) => {
    await search(e, this.rootItems);
    const expandedKeys = getExpandedKeys(this.rootItems);
    this.setState({expandedKeys, search: e});
  };

  render () {
    let content = <LoadingView />;
    if (!this.props.storages.pending && this.props.storages.error) {
      content = <Alert message="Error retrieving data storages" type="error" />;
    } else if (!this.props.storages.pending) {
      const table = this.getStorageItemsTable();
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
          {
            !this.state.bucket
              ? <Alert type="info" message="Select data storage" />
              : <div>
                <Table
                  className={styles.table}
                  dataSource={table.data}
                  columns={table.columns}
                  loading={this.storageIsFetching}
                  rowKey="key"
                  pagination={false}
                  rowClassName={(item) => styles[item.type.toLowerCase()]}
                  locale={{emptyText: 'Folder is empty'}}
                  size="small" />
                <Row key="pagination"
                  type="flex"
                  justify="end"
                  style={{marginTop: 10, paddingRight: 15}}>
                  <Button
                    id="prev-page-button"
                    onClick={this.prevPage}
                    disabled={this.state.currentPage === 0}
                    style={{margin: 3}} size="small"><Icon type="caret-left" /></Button>
                  <Button
                    id="next-page-button"
                    onClick={this.nextPage}
                    disabled={this.state.pageMarkers.length <= this.state.currentPage + 1}
                    style={{margin: 3}} size="small"><Icon type="caret-right" /></Button>
                </Row>
              </div>
          }
        </SplitPane>
      );
    }

    let itemsSelectedCount = 0;
    if (this.props.multiple && this.state.selectedItems) {
      itemsSelectedCount = this.state.selectedItems.length;
    }

    return (
      <Modal
        width="80%"
        title="Select folder or file"
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
                disabled={this.state.selectedItems.length === 0}
                onClick={() => this.onSelectClicked()}>
                OK{
                  itemsSelectedCount > 0
                    ? (itemsSelectedCount > 1 ? ` (${itemsSelectedCount} items)` : ' (1 item)')
                    : ''
                }
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

  componentWillReceiveProps (nextProps) {
    if (nextProps.path !== this.props.path) {
      let path = nextProps.path;
      const firstItemPath = (path || '').split(',').map(p => p.trim())[0];
      let bucket = this.getBucketByPath(firstItemPath);
      if (bucket) {
        const bucketKey = bucket.type === DTS_ROOT_ITEM_TYPE
            ? `${DTS_ROOT_ITEM_TYPE}_${bucket.id}_${bucket.prefix}`
            : `storage_${bucket.id}`;
        let expandedKeys = this.state.expandedKeys;
        if (this.rootItems) {
          const item = getTreeItemByKey(
            bucketKey,
            this.rootItems
          );
          if (item) {
            expandItem(item, this.rootItems);
            expandedKeys = getExpandedKeys(this.rootItems);
          }
        }
        let pathCorrected;
        if (bucket.type === DTS_ROOT_ITEM_TYPE) {
          const prefix = `${bucket.prefix}/`.replace(/\/\//g, '/');
          pathCorrected = this.parentDirectory(firstItemPath.substring(prefix.length));
        } else {
          pathCorrected = this.parentDirectory(this.fixPath(firstItemPath));
        }
        this.setState(
          {
            bucket: bucket,
            expandedKeys,
            selectedKeys: [bucketKey],
            path: decodeURIComponent(pathCorrected || ''),
            selectedItems: (path || '').split(',').map(p => ({name: p.trim()}))
          });
      }
    }
  }

  prevPage = () => {
    if (this.state.currentPage > 0) {
      const currentPage = this.state.currentPage - 1;
      const marker = this.state.pageMarkers[currentPage];
      this.storage.fetchPage(marker);
      this.setState({
        currentPage,
        pagePerformed: false
      });
    }
  };

  nextPage = () => {
    if (this.state.currentPage + 1 < this.state.pageMarkers.length) {
      const currentPage = this.state.currentPage + 1;
      const marker = this.state.pageMarkers[currentPage];
      this.storage.fetchPage(marker);
      this.setState({
        currentPage,
        pagePerformed: false
      });
    }
  };

  performPage = () => {
    const pageMarkers = this.state.pageMarkers;
    if (!this.storage.error) {
      if (this.storage.value.nextPageMarker) {
        if (pageMarkers.length > this.state.currentPage + 1) {
          pageMarkers[this.state.currentPage + 1] = this.storage.value.nextPageMarker;
        } else {
          pageMarkers.push(this.storage.value.nextPageMarker);
        }
      } else {
        pageMarkers.splice(this.state.currentPage + 1, pageMarkers.length - this.state.currentPage - 1);
      }
    }
    this.setState({
      pagePerformed: true,
      pageMarkers
    });
  };

  componentDidUpdate (prevProps) {
    if (this.props.storages.loaded &&
        this.props.dtsList.loaded &&
      !this.state.bucket &&
      this.props.storages.value.length && this.props.path) {
      let firstItemPath = (this.props.path || '').split(',').map(p => p.trim())[0];
      let bucket = this.getBucketByPath(firstItemPath);
      if (bucket && bucket.type === DTS_ROOT_ITEM_TYPE && firstItemPath) {
        const prefix = bucket.prefix.replace(/\/\//g, '/');
        firstItemPath = firstItemPath.substring(prefix.length);
      }
      let expandedKeys = this.state.expandedKeys;
      if (bucket && this.rootItems) {
        const item = getTreeItemByKey(
          bucket.type === DTS_ROOT_ITEM_TYPE
            ? `${DTS_ROOT_ITEM_TYPE}_${bucket.id}_${bucket.prefix}`
            : `storage_${bucket.id}`,
          this.rootItems
        );
        if (item) {
          expandItem(item, this.rootItems);
          expandedKeys = getExpandedKeys(this.rootItems);
        }
      }
      let pathCorrected;
      if (bucket && bucket.type === DTS_ROOT_ITEM_TYPE) {
        const prefix = `${bucket.prefix}/`.replace(/\/\//g, '/');
        pathCorrected = firstItemPath.substring(prefix.length);
      } else {
        pathCorrected = firstItemPath;
      }
      pathCorrected = this.parentDirectory(pathCorrected);
      if (bucket) {
        this.setState({
          bucket,
          expandedKeys,
          selectedKeys: [`storage_${bucket.id}`],
          path: decodeURIComponent(pathCorrected || ''),
          pageMarkers: [null],
          pagePerformed: false,
          currentPage: 0
        });
      } else if (this.state.path !== firstItemPath) {
        this.setState({
          selectedKeys: [],
          path: decodeURIComponent(firstItemPath || ''),
          pageMarkers: [null],
          pagePerformed: false,
          currentPage: 0
        });
      }
    } else if (this.state.bucket &&
      (this.storage === null ||
      `${this.storage.id}` !== `${this.state.bucket.id}` ||
      (this.storage.type === DTS_ROOT_ITEM_TYPE && `${this.storage.prefix}` !== `${this.state.bucket.prefix}`) ||
      `${this.storage.type}` !== `${this.state.bucket.type}`)) {
      if (this.state.bucket.type === DTS_ROOT_ITEM_TYPE) {
        this.storage = new DTSRequest(this.state.bucket.id, this.state.bucket.prefix, this.state.path, PAGE_SIZE);
      } else {
        this.storage = new DataStorageRequest(
          this.state.bucket.id,
          this.state.path,
          false,
          PAGE_SIZE
        );
      }
      this.storage.type = this.state.bucket.type;
      this.storage.fetch();
      this.setState({
        pageMarkers: [null],
        pagePerformed: false,
        currentPage: 0
      });
    } else if (this.storage && this.storage.path !== this.state.path) {
      if (this.storage.type === DTS_ROOT_ITEM_TYPE) {
        this.storage = new DTSRequest(this.state.bucket.id, this.state.bucket.prefix, this.state.path, PAGE_SIZE);
      } else {
        this.storage = new DataStorageRequest(
          this.state.bucket.id,
          this.state.path,
          false,
          PAGE_SIZE
        );
      }
      this.storage.type = this.state.bucket.type;
      this.storage.fetch();
      this.setState({
        pageMarkers: [null],
        pagePerformed: false,
        currentPage: 0
      });
    } else if (this.storage && !this.storage.pending && !this.state.pagePerformed) {
      this.performPage();
    }

    if (this.props.visible && this.props.visible !== prevProps.visible) {
      this.rootItems = null;
      this.props.storages.fetch();
      this.props.library.fetch();
      this.setState({
        search: null
      });
    }
  }
}
