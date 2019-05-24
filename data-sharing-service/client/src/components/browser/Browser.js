/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {inject, observer} from 'mobx-react';
import {
  Alert,
  Button,
  Checkbox,
  Col,
  Icon,
  Input,
  message,
  Modal,
  Row,
  Spin,
  Table
} from 'antd';
import {EditableField, LoadingView, UploadButton} from '../special';
import DataStorageRequest from '../../models/dataStorage/DataStoragePage';
import DataStorageItemUpdate from '../../models/dataStorage/DataStorageItemUpdate';
import DataStorageItemDelete from '../../models/dataStorage/DataStorageItemDelete';
import GenerateDownloadUrlRequest from '../../models/dataStorage/GenerateDownloadUrl';
import GenerateDownloadUrlsRequest from '../../models/dataStorage/GenerateDownloadUrls';
import {EditItemForm, Navigation} from './forms';
import parseQueryParameters from '../../utils/queryParameters';
import displayDate from '../../utils/displayDate';
import roleModel from '../../utils/roleModel';
import styles from './Browser.css';
import {NoStorage} from '../main/App';

const PAGE_SIZE = 40;

@inject('dataStorages', 'S3Storage')
@inject(({routing, dataStorages}, {params}) => {
  const queryParameters = parseQueryParameters(routing);
  return {
    storageId: queryParameters.id,
    path: queryParameters.path,
    storage: queryParameters.id ? new DataStorageRequest(queryParameters.id, queryParameters.path, false, PAGE_SIZE) : null,
    info: queryParameters.id ? dataStorages.load(queryParameters.id) : null
  };
})
@observer
export default class Browser extends React.Component {

  state = {
    downloadUrlModalVisible: false,
    selectedItems: [],
    renameItem: null,
    createFolder: false,
    currentPage: 0,
    itemsToDelete: null,
    pageMarkers: [null],
    pagePerformed: false
  };

  refreshList = async () => {
    await this.props.info.fetch();
    await this.props.storage.fetchPage(null);
    this.setState({
      currentPage: 0,
      pageMarkers: [null],
      pagePerformed: false
    });
  };

  isDataRefreshing = () => {
    return this.props.storage.pending;
  };

  afterDataStorageEdit = () => {
    this.props.info.fetch();
  };

  navigate = (id, path) => {
    if (path && path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    if (path) {
      this.props.router.push(`${process.env.PUBLIC_URL}?id=${id}&path=${path}`);
    } else {
      this.props.router.push(`${process.env.PUBLIC_URL}?id=${id}`);
    }
    this.setState({currentPage: 0, pageMarkers: [null], pagePerformed: false, selectedItems: []});
  };

  navigateFull = (path) => {
    if (path && !this.props.info.pending && !this.props.info.error) {
      const parts = path.split('://');
      if (parts.length === 2) {
        const nameParts = parts[1].split('/');
        const bucketName = nameParts[0];
        let relativePath;
        for (let i = 1; i < nameParts.length; i++) {
          if (!relativePath) {
            relativePath = nameParts[i];
          } else {
            relativePath += `/${nameParts[i]}`;
          }
        }
        if (this.props.info.value.path.toLowerCase() !== bucketName.toLowerCase()) {
          message.error('You cannot navigate to another storage.', 3);
        } else {
          if (relativePath && relativePath.endsWith('/')) {
            relativePath = path.substring(0, relativePath.length - 1);
          }
          if (relativePath) {
            this.props.router.push(`${process.env.PUBLIC_URL}?id=${this.props.storageId}&path=${relativePath}`);
          } else {
            this.props.router.push(`${process.env.PUBLIC_URL}?id=${this.props.storageId}`);
          }
          this.setState({currentPage: 0, pageMarkers: [null], pagePerformed: false, selectedItems: []});
        }
      }
    }
  };

  canGoToParent () {
    return this.props.path;
  }

  parentDirectory () {
    if (this.props.path) {
      const parts = this.props.path.split('/');
      if (parts.length > 1) {
        parts.pop();
        return parts.join('/');
      }
    }
    return undefined;
  }

  sizeRenderer = (size) => {
    if (isNaN(size)) {
      return size;
    }
    let sizeValue = +size;
    const sizePostfix = ['bytes', 'Kb', 'Mb', 'Gb', 'Tb', 'Pb', 'Eb', 'Zb', 'Yb'];
    let index = 0;
    while (sizeValue > 1024 && index < sizePostfix.length - 1) {
      index += 1;
      sizeValue /= 1024;
    }
    if (index === 0) {
      return `${sizeValue} ${sizePostfix[index]}`;
    }
    return `${sizeValue.toFixed(2)} ${sizePostfix[index]}`;
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

  downloadSingleFile = async (event, item) => {
    event.stopPropagation();
    const hide = message.loading(`Fetching ${item.name} url...`, 0);
    const request = new GenerateDownloadUrlRequest(this.props.storageId, item.path, item.version);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      hide();
      const a = document.createElement('a');
      a.href = request.value.url;
      a.download = item.name;
      a.style.display = 'none';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }
  };

  toggleGenerateDownloadUrlsModalFn = () => {
    let downloadUrlModalVisible = !this.state.downloadUrlModalVisible;
    if (downloadUrlModalVisible && this.state.selectedItems) {
      this.generateDownloadUrls = new GenerateDownloadUrlsRequest(this.props.storageId);
      this.generateDownloadUrls.send({
        paths: this.state.selectedItems.map(i => i.path)
      });
    } else {
      this.generateDownloadUrls = null;
    }
    this.setState({downloadUrlModalVisible});
  };

  closeDownloadUrlModal = () => {
    this.setState({downloadUrlModalVisible: false});
  };

  openRenameItemDialog = (event, item) => {
    event.stopPropagation();
    this.setState({renameItem: item});
  };

  closeRenameItemDialog = () => {
    this.setState({renameItem: null});
  };

  renameItem = async ({name}) => {
    const hide = message.loading(`Renaming ${this.state.renameItem.name}...`, 0);
    const request = new DataStorageItemUpdate(this.props.storageId);
    let path = this.props.path || '';
    if (path.length > 0 && !path.endsWith('/')) {
      path += '/';
    }
    const payload = [{
      oldPath: this.state.renameItem.path,
      path: decodeURIComponent(`${path}${name}`),
      type: this.state.renameItem.type,
      action: 'Move'
    }];
    await request.send(payload);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeRenameItemDialog();
      await this.refreshList();
    }
  };

  openCreateFolderDialog = () => {
    this.setState({createFolder: true});
  };

  closeCreateFolderDialog = () => {
    this.setState({createFolder: false});
  };

  createFolder = async ({name}) => {
    const hide = message.loading(`Creating folder '${name}'...`, 0);
    const request = new DataStorageItemUpdate(this.props.storageId);
    let path = this.props.path || '';
    if (path.length > 0 && !path.endsWith('/')) {
      path += '/';
    }
    const payload = [{
      path: decodeURIComponent(`${path}${name}`),
      type: 'Folder',
      action: 'Create'
    }];
    await request.send(payload);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeCreateFolderDialog();
      await this.refreshList();
    }
  };

  closeDeleteModal = () => {
    this.setState({itemsToDelete: null});
  };

  removeItemConfirm = (event, item) => {
    event.stopPropagation();
    const removeItem = () => this.removeItems([item], false, true);
    let content = `Are you sure you want to delete ${item.type.toLowerCase()} '${item.name}'?`;
    if (item.type.toLowerCase() === 'folder') {
      content = (
        <div>
          <Row>
            {content}
          </Row>
          <Row>
            All child folders and files will be removed.
          </Row>
        </div>
      );
    }
    Modal.confirm({
      title: `Remove ${item.type.toLowerCase()}`,
      content: content,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        removeItem();
      }
    });
  };

  removeItems = async (items, totally, clearSelectedItems, afterRemove) => {
    let removeMessage;
    if (items && items.length === 1) {
      removeMessage = `Removing '${items[0].name || items[0].path}'...`;
    } else if (items && items.length > 1) {
      removeMessage = `Removing '${items.length} items'...`;
    } else {
      return;
    }
    const hide = message.loading(removeMessage);
    const request = new DataStorageItemDelete(this.props.storageId, totally);
    await request.send(items.map(item => {
      return {
        path: item.path,
        type: item.type
      };
    }));
    hide();
    if (request.error) {
      message.error(request.error, 3);
    } else {
      if (clearSelectedItems) {
        const selectedItems = this.state.selectedItems;
        const removeItemFromSelectedList = (item) => {
          const [selectedItem] = selectedItems.filter(s => s.name === item.name && s.type === item.type);
          if (selectedItem) {
            const index = selectedItems.indexOf(selectedItem);
            selectedItems.splice(index, 1);
          }
        };
        items.forEach(item => removeItemFromSelectedList(item));
        this.setState({selectedItems});
      }
      this.setState({
        pagePerformed: false
      }, async () => {
        await this.refreshList();
        afterRemove && afterRemove();
      });
    }
  };

  removeSelectedItemsConfirm = (event) => {
    const items = this.state.selectedItems.map(item => {
      return {
        path: item.path,
        type: item.type
      };
    });
    event.stopPropagation();
    const removeItems = () => this.removeItems(items, false, false, () => {
      this.setState({selectedItems: []});
    });
    Modal.confirm({
      title: 'Remove all selected items?',
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        removeItems();
      }
    });
  };

  actionsRenderer = (type, item) => {
    const actions = [];
    let separatorIndex = 0;
    const separator = () => {
      separatorIndex += 1;
      return (
        <div
          key={`separator_${separatorIndex}`}
          style={{
            marginLeft: 5,
            width: 3,
            height: 12,
            display: 'inline-block'
          }} />
      );
    };
    if (item.downloadable) {
      actions.push(
        <Button
          key="download"
          onClick={(event) => this.downloadSingleFile(event, item)}
          size="small"><Icon type="download" /></Button>
      );
    }
    if (item.editable) {
      actions.push(
        <Button
          key="rename"
          size="small"
          onClick={(event) => this.openRenameItemDialog(event, item)}>
          <Icon type="edit" />
        </Button>
      );
    }
    if (item.deletable &&
      (item.type.toLowerCase() === 'folder' ||
      (item.type.toLowerCase() !== 'folder' && (!item.isVersion || item.latest)))) {
      actions.push(separator());
      actions.push(
        <Button
          key="remove"
          type="danger"
          size="small"
          onClick={(event) => this.removeItemConfirm(event, item)}>
          <Icon type="delete" />
        </Button>
      );
    }
    return (
      <div className={styles.itemActionsContainer}>
        {actions}
      </div>
    );
  };

  fileIsSelected = (item) => {
    return this.state.selectedItems.filter(s => s.name === item.name && s.type === item.type).length === 1;
  };

  selectFile = (item) => () => {
    const selectedItems = this.state.selectedItems;
    const [selectedItem] = this.state.selectedItems.filter(s => s.name === item.name && s.type === item.type);
    if (selectedItem) {
      const index = selectedItems.indexOf(selectedItem);
      selectedItems.splice(index, 1);
    } else {
      selectedItems.push(item);
    }
    this.setState({selectedItems});
  };

  getStorageItemsTable = () => {
    const getList = () => {
      const items = [];
      if (this.canGoToParent()) {
        items.push({
          key: `folder_${this.parentDirectory()}`,
          name: '..',
          path: this.parentDirectory(),
          type: 'folder',
          downloadable: false,
          editable: false,
          selectable: false
        });
      }
      let results = [];
      if (this.props.storage.loaded) {
        results = this.props.storage.value.results || [];
      }
      items.push(...results.map(i => {
        return {
          key: `${i.type}_${i.path}`,
          ...i,
          downloadable: i.type.toLowerCase() === 'file' && !i.deleteMarker &&
          (
            !i.labels ||
            !i.labels['StorageClass'] ||
            i.labels['StorageClass'].toLowerCase() !== 'glacier'
          ),
          editable: roleModel.writeAllowed(this.props.info.value) && !i.deleteMarker,
          deletable: roleModel.writeAllowed(this.props.info.value),
          selectable: !i.deleteMarker
        };
      }));
      return items;
    };

    this.tableData = this.props.storage.pending ? (this.tableData || []) : getList();
    const selectionColumn = {
      key: 'selection',
      title: '',
      className: styles.checkboxCell,
      render: (item) => {
        if (item.selectable && (item.downloadable || item.editable)) {
          return (
            <Checkbox
              checked={this.fileIsSelected(item)}
              onChange={this.selectFile(item)} />
          );
        } else {
          return <span />;
        }
      }
    };
    const typeColumn = {
      dataIndex: 'type',
      key: 'type',
      title: '',
      className: styles.itemTypeCell,
      onCellClick: (item) => this.didSelectDataStorageItem(item),
      render: (text, item) => <Icon className={styles.itemType} type={item.type.toLowerCase()} />
    };
    const nameColumn = {
      dataIndex: 'name',
      key: 'name',
      title: 'Name',
      className: styles.nameCell,
      render: (text, item) => {
        if (item.latest) {
          return `${text} (latest)`;
        }
        return text;
      },
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const sizeColumn = {
      dataIndex: 'size',
      key: 'size',
      title: 'Size',
      className: styles.sizeCell,
      render: this.sizeRenderer,
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const changedColumn = {
      dataIndex: 'changed',
      key: 'changed',
      title: 'Date changed',
      className: styles.changedCell,
      render: (date) => date ? displayDate(date) : '',
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const labelsColumn = {
      dataIndex: 'labels',
      key: 'labels',
      title: '',
      className: styles.labelsCell,
      render: this.labelsRenderer,
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const actionsColumn = {
      key: 'actions',
      className: styles.itemActions,
      render: this.actionsRenderer
    };

    const columns = [];
    columns.push(selectionColumn);
    columns.push(typeColumn);
    columns.push(nameColumn);
    columns.push(sizeColumn);
    columns.push(changedColumn);
    columns.push(labelsColumn);
    columns.push(actionsColumn);

    return {
      columns,
      data: this.tableData
    };
  };

  didSelectDataStorageItem = (item) => {
    if (item.type.toLowerCase() === 'folder') {
      let path = item.path;
      if (path && path.endsWith('/')) {
        path = path.substring(0, path.length - 1);
      }
      if (path) {
        this.props.router.push(`${process.env.PUBLIC_URL}?id=${this.props.storageId}&path=${path}`);
      } else {
        this.props.router.push(`${process.env.PUBLIC_URL}?id=${this.props.storageId}`);
      }
      this.setState({
        currentPage: 0,
        pageMarkers: [null],
        pagePerformed: false,
        selectedItems: []
      });
    } else if (item.type.toLowerCase() === 'file' && !item.deleteMarker) {

    }
  };

  get bulkDownloadEnabled () {
    const selectedItemsLength = this.state.selectedItems.length;
    const downloadableSelectedItemsLength = this.state.selectedItems
      .filter(item => item.downloadable).length;
    return selectedItemsLength > 0 &&
      selectedItemsLength === downloadableSelectedItemsLength;
  }

  get removeAllSelectedItemsEnabled () {
    const selectedItemsLength = this.state.selectedItems.length;
    const editableSelectedItemsLength = this.state.selectedItems
      .filter(item => item.editable).length;
    return selectedItemsLength > 0 &&
      selectedItemsLength === editableSelectedItemsLength;
  }

  get selectAllAvailable () {
    if (this.props.storage.loaded &&
      this.props.storage.value &&
      this.props.storage.value.results) {
      let allSelected = false;
      if (this.state.selectedItems) {
        allSelected = true;
        const values = (this.props.storage.value.results || []);
        for (let i = 0; i < values.length; i++) {
          const value = values[i];
          if (this.state.selectedItems.filter(si => si.path === value.path && si.type === value.type).length === 0) {
            allSelected = false;
            break;
          }
        }
      }
      return !allSelected;
    }
    return false;
  }

  get clearSelectionVisible () {
    return this.state.selectedItems.length > 0;
  }

  selectAll = (type) => {
    if (this.props.storage.loaded && this.tableData) {
      const selectedItems = this.tableData.filter(item => {
        if (!item.editable && !item.downloadable) {
          return false;
        }
        if (type) {
          return item.type.toLowerCase() === type.toLowerCase();
        } else {
          return true;
        }
      });
      this.setState({selectedItems});
    }
  };

  clearSelection = () => {
    this.setState({selectedItems: []});
  };

  prevPage = () => {
    if (this.state.currentPage > 0) {
      const currentPage = this.state.currentPage - 1;
      const marker = this.state.pageMarkers[currentPage];
      this.props.storage.fetchPage(marker);
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
      this.props.storage.fetchPage(marker);
      this.setState({
        currentPage,
        pagePerformed: false
      });
    }
  };

  render () {
    if (!this.props.storageId) {
      return <NoStorage />;
    }
    if (!this.props.info.loaded && this.props.info.pending) {
      return <LoadingView />;
    }
    if (this.props.info.error) {
      return <Alert message={this.props.info.error} type="error" />;
    }
    let contents;
    if (!this.props.storage.error) {
      const table = this.getStorageItemsTable();
      const title = () => {
        return (
          <Row
            className={styles.storageActions}
            type="flex"
            justify="space-between">
            <div>
              {
                this.selectAllAvailable &&
                (
                  <Button
                    id="select-all-button"
                    size="small" onClick={() => this.selectAll(undefined)}>
                    Select page
                  </Button>
                )
              }
              {
                this.clearSelectionVisible &&
                <Button
                  style={{marginLeft: 5}}
                  id="clear-selection-button"
                  size="small" onClick={() => this.clearSelection()}>
                  Clear selection
                </Button>
              }
            </div>
            <div style={{paddingRight: 8}}>
              {
                this.bulkDownloadEnabled &&
                <Button
                  id="bulk-url-button"
                  size="small"
                  onClick={this.toggleGenerateDownloadUrlsModalFn}>
                  Generate URL
                </Button>
              }
              {
                this.removeAllSelectedItemsEnabled &&
                roleModel.writeAllowed(this.props.info.value) &&
                <Button
                  id="remove-all-selected-button"
                  size="small"
                  onClick={(e) => this.removeSelectedItemsConfirm(e)}
                  type="danger">
                  Remove all selected
                </Button>
              }
              {
                this.removeAllSelectedItemsEnabled && this.bulkDownloadEnabled &&
                <div style={{display: 'inline-block', marginLeft: 10, width: 2, height: 2}} />
              }
              {
                roleModel.writeAllowed(this.props.info.value) &&
                <Button
                  type="primary"
                  id="create-button"
                  onClick={this.openCreateFolderDialog}
                  size="small">
                  <Icon type="plus" /> Create folder
                </Button>
              }
              {
                roleModel.writeAllowed(this.props.info.value) &&
                <UploadButton
                  multiple={true}
                  onRefresh={this.refreshList}
                  title={'Upload'}
                  storageId={this.props.storageId}
                  path={this.props.path}
                  uploadToS3={this.props.info.value.type === 'S3'}
                  action={DataStorageItemUpdate.uploadUrl(this.props.storageId, this.props.path)} />
              }
            </div>
          </Row>
        );
      };

      contents = [
        <Table
          className={styles.table}
          style={{flex: 1}}
          key="table"
          dataSource={table.data}
          columns={table.columns}
          loading={this.props.storage.pending}
          title={title}
          rowKey="key"
          pagination={false}
          rowClassName={(item) => `${styles[item.type.toLowerCase()]} ${item.deleteMarker ? styles.deleteMarker : ''}`}
          locale={{emptyText: 'Folder is empty'}}
          size="small" />,
        <Row key="pagination" type="flex" justify="end" style={{marginTop: 10, marginBottom: 10, paddingRight: 15}}>
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
      ];
    } else if (this.props.storage.error) {
      contents = (
        <div>
          <br />
          <Alert
            message={`Error retrieving data storage items: ${this.props.storage.error}`} type="error" />
        </div>
      );
    } else {
      contents = (
        <div>
          <Row type="flex" justify="center">
            <br />
            <Spin />
          </Row>
        </div>
      );
    }

    const storageTitleClassName = this.props.info.value.locked ? styles.readonly : undefined;

    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row type="flex" justify="space-between" align="middle">
          <Col span={12}>
            <Row type="flex" className={styles.itemHeader} align="middle">
              <Icon type="hdd" className={`${styles.editableControl} ${storageTitleClassName}`} />
              {
                this.props.info.value.locked &&
                <Icon
                  className={`${styles.editableControl} ${storageTitleClassName}`}
                  type="lock" />
              }
              <EditableField
                allowEpmty={false}
                readOnly={true}
                editStyle={{flex: 1}}
                text={this.props.info.value.name}
                onSave={() => {}} />
            </Row>
          </Col>
          <Col span={12}>
            <Row type="flex" justify="end" className={styles.currentFolderActions}>
              <Button
                id="refresh-storage-button"
                size="small"
                onClick={() => this.refreshList()}
                disabled={this.isDataRefreshing()}>Refresh</Button>
            </Row>
          </Col>
        </Row>
        <div
          style={{flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto'}}>
          <Row className={styles.dataStorageInfoContainer}>
            {
              this.props.info.value.description &&
              <Row><b>Description: </b>{this.props.info.value.description}</Row>
            }
          </Row>
          <Row style={{marginLeft: 5}}>
            <Navigation
              path={this.props.path}
              storage={this.props.info.value}
              navigate={this.navigate}
              navigateFull={this.navigateFull} />
          </Row>
          {contents}
        </div>
        <Modal
          title="Download file url"
          width="80%"
          visible={this.state.downloadUrlModalVisible}
          onOk={() => this.closeDownloadUrlModal(true)}
          onCancel={() => this.closeDownloadUrlModal()}
          afterClose={() => { this.generateDownloadUrls = null; }}
          footer={
            <Button
              type="primary"
              onClick={() => this.closeDownloadUrlModal(true)}>
              OK
            </Button>
          }>
          {this.generateDownloadUrls && (!this.generateDownloadUrls.pending
            ? (
              <Input
                type="textarea"
                className={styles.generateDownloadUrlInput}
                rows={10}
                value={this.generateDownloadUrls.value.map(u => u.url).join('\n')} />
            )
            : <div><Row type="flex" justify="center"><br /><Spin /></Row></div>)
          }
        </Modal>
        <EditItemForm
          pending={false}
          title="Create folder"
          visible={this.state.createFolder}
          onCancel={this.closeCreateFolderDialog}
          onSubmit={this.createFolder} />
        <EditItemForm
          pending={false}
          title={this.state.renameItem
            ? (
              this.state.renameItem.type.toLowerCase() === 'file'
                ? 'Rename file'
                : 'Rename folder'
            )
            : null
          }
          name={this.state.renameItem ? this.state.renameItem.name : null}
          visible={!!this.state.renameItem}
          onCancel={this.closeRenameItemDialog}
          onSubmit={this.renameItem} />
        <Modal
          visible={!!this.state.itemsToDelete}
          onCancel={this.closeDeleteModal}
          title="Do you want to delete item(s) from bucket or set 'Deletion' marker?"
          footer={
            <Row type="flex" justify="space-between">
              <Col span={8}>
                <Row type="flex" justify="start">
                  <Button
                    id="delete-bucket-item-modal-cancel-button"
                    onClick={this.closeDeleteModal}>Cancel</Button>
                </Row>
              </Col>
              <Col span={16}>
                <Row type="flex" justify="end">
                  <Button
                    id="delete-bucket-item-modal-set-deletion-marker-button"
                    type="danger"
                    onClick={() => this.removeItems(this.state.itemsToDelete, false, false, () => {
                      this.closeDeleteModal();
                      this.setState({selectedItems: []});
                      this.afterDataStorageEdit();
                    })}>Set deletion marker</Button>
                  <Button
                    id="delete-bucket-item-modal-delete-from-bucket-button"
                    type="danger"
                    onClick={() => this.removeItems(this.state.itemsToDelete, true, false, () => {
                      this.closeDeleteModal();
                      this.setState({selectedItems: []});
                      this.afterDataStorageEdit();
                    })}>Delete from bucket</Button>
                </Row>
              </Col>
            </Row>
          }>
          {
            (this.state.itemsToDelete || []).map(item => {
              return (
                <Row key={item.name}>{item.name}</Row>
              );
            })
          }
        </Modal>
      </div>
    );
  }

  performPage = () => {
    const pageMarkers = this.state.pageMarkers;
    if (!this.props.storage.error) {
      if (this.props.storage.value.nextPageMarker) {
        if (pageMarkers.length > this.state.currentPage + 1) {
          pageMarkers[this.state.currentPage + 1] = this.props.storage.value.nextPageMarker;
        } else {
          pageMarkers.push(this.props.storage.value.nextPageMarker);
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
    if (this.props.info.value.type === 'S3') {
      if ((prevProps.path !== this.props.path) || !this.props.S3Storage.prefix) {
        this.props.S3Storage.prefix = this.props.path ? this.props.path : '';
      }
      if (this.props.info.loaded && (
        (prevProps.storageId !== this.props.storageId) ||
        (!this.props.S3Storage.storage && this.props.storageId))) {
        this.props.S3Storage.storage = this.props.info.value;
      }
    } else {
      this.props.S3Storage.prefix = '';
      this.props.S3Storage.storage = null;
    }
    if (prevProps.storageId !== this.props.storageId) {
      this.setState({
        metadata: undefined,
        pageMarkers: [null],
        currentPage: 0,
        pagePerformed: false,
        selectedItems: []
      });
    } else if (!this.props.storage.pending && !this.state.pagePerformed) {
      this.performPage();
    }
  }
}
