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
import {computed} from 'mobx';
import connect from '../../../utils/connect';
import {Checkbox, Modal, Table, Icon, Row, Col, Button, message} from 'antd';
import {ItemTypes, generateTreeData} from '../model/treeStructureFunctions';
import styles from './Browser.css';
import roleModel from '../../../utils/roleModel';
import folders from '../../../models/folders/Folders';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import MetadataEntityUpload from '../../../models/folderMetadata/MetadataEntityUpload';
import MetadataEntitySave from '../../../models/folderMetadata/MetadataEntitySave';
import MetadataEntityDeleteFromProject from '../../../models/folderMetadata/MetadataEntityDeleteFromProject';
import MetadataEntityFields from '../../../models/folderMetadata/MetadataEntityFields';
import UploadButton from '../../special/UploadButton';
import AddInstanceForm from './forms/AddInstanceForm';
import PropTypes from 'prop-types';
import Breadcrumbs from '../../special/Breadcrumbs';

@connect({
  folders,
  pipelinesLibrary
})
@inject(({folders, pipelinesLibrary}, params) => {
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    folder: componentParameters.id ? folders.load(componentParameters.id) : pipelinesLibrary,
    folderId: componentParameters.id,
    entityFields: new MetadataEntityFields(componentParameters.id),
    onReloadTree: params.onReloadTree
  };
})
@observer
export default class MetadataFolder extends React.Component {

  static propTypes = {
    selectionAvailable: PropTypes.bool,
    hideUploadMetadataBtn: PropTypes.bool,
    onNavigate: PropTypes.func,
    onSelectItem: PropTypes.func,
    initialSelection: PropTypes.array
  };

  _currentFolder = null;

  state = {
    columns: [
      {
        key: 'type',
        className: styles.treeItemType,
        render: (item) => this.renderTreeItemType(item),
        onCellClick: (item) => this.navigate(item)
      },
      {
        key: 'name',
        title: 'Name',
        className: styles.treeItemName,
        render: (item) => this.renderItemName(item),
        onCellClick: (item) => this.navigate(item)
      }
    ],
    selectedItems: this.props.initialSelection ? this.props.initialSelection : [],
    addInstanceFormVisible: false,
    operationInProgress: false
  };

  @computed
  get entityTypes () {
    if (this.props.entityFields.loaded) {
      return (this.props.entityFields.value || []).map(e => e);
    }
    return [];
  }

  operationWrapper = (operation) => (...props) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        operationInProgress: false
      });
    });
  };

  openAddInstanceForm = () => {
    this.setState({
      addInstanceFormVisible: true
    });
  };

  closeAddInstanceForm = () => {
    this.setState({
      addInstanceFormVisible: false
    });
  };

  addInstance = async (values) => {
    const classId = +values.entityClass;
    const [metadataClass] = this.entityTypes.map(e => e.metadataClass).filter(m => m.id === classId);
    if (metadataClass) {
      const className = metadataClass.name;
      const payload = {
        classId,
        className,
        externalId: values.id,
        data: values.data,
        parentId: this.props.folderId
      };
      const request = new MetadataEntitySave();
      await request.send(payload);
      if (request.error) {
        message.error(request.error, 5);
      } else {
        await this.props.folder.fetch();
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this.props.folder.value.parentId);
        }
        this.closeAddInstanceForm();
      }
    } else {
      message.error('Unknown metadata class', 5);
    }
  };

  @computed
  get hasSelectionColumn () {
    return this.state.columns && this.state.columns[0].key === 'selection';
  }

  renderItemName = (item) => {
    return item.amount ? <span>{`${item.name} [${item.amount}]`}</span> : <span>{item.name}</span>;
  };

  renderTreeItemType = (item) => {
    switch (item.type) {
      case ItemTypes.metadata: return <Icon type="appstore-o" />;
      default: return <div />;
    }
  };

  navigate = (item) => {
    if (this.props.onNavigate) {
      this.props.onNavigate(item);
    } else {
      this.props.router.push(item.url());
    }
  };

  fileIsSelected = (item) => {
    return this.state.selectedItems
      .filter(s => s.id === item.id && s.type === item.type).length === 1;
  };

  selectFile = (item) => () => {
    const selectedItems = this.state.selectedItems;
    const [selectedItem] = this.state.selectedItems
      .filter(s => s.id === item.id && s.type === item.type);
    if (selectedItem) {
      const index = selectedItems.indexOf(selectedItem);
      selectedItems.splice(index, 1);
    } else {
      selectedItems.push(item);
    }
    this.setState({selectedItems});
    if (this.props.onSelectItem) {
      this.props.onSelectItem(this.state.selectedItems);
    }
  };

  renderSelectionColumn = () => ({
    key: 'selection',
    title: '',
    className: styles.checkboxCell,
    render: (item) => {
      if (item.type === ItemTypes.metadata) {
        return (
          <Checkbox
            disabled={this.state.selectedItems.length > 0 && !this.fileIsSelected(item)}
            checked={this.fileIsSelected(item)}
            onChange={this.selectFile(item)}
          />
        );
      } else {
        return <span />;
      }
    }
  });

  renderContent = () => {
    return (
      <Row style={{padding: 5, overflowY: 'auto'}}>
        <Table
          className={styles.childrenContainer}
          dataSource={this._currentFolder.data}
          columns={this.state.columns}
          rowKey={(item) => item.key}
          title={null}
          showHeader={false}
          rowClassName={(item) => `folder-item-${item.key}`}
          expandedRowRender={null}
          loading={false}
          pagination={{pageSize: 40}}
          locale={{emptyText: 'Metadata is empty'}}
          size="small" />
      </Row>);
  };

  deleteMetadataConfirm = () => {
    const onDeleteMetadata = async () => {
      const hide = message.loading('Removing metadata...', -1);
      const request = new MetadataEntityDeleteFromProject(this.props.folderId);
      await request.fetch();
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.folder.fetch();
        hide();
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this.props.folder.value.parentId);
        }
        this.props.router.push(`/folder/${this.props.folderId}`);
      }
    };
    Modal.confirm({
      title: 'Delete metadata?',
      onOk: onDeleteMetadata
    });
  };

  renderActions = () => {
    const actions = [];
    if (roleModel.writeAllowed(this.props.folder.value) &&
      this.props.folderId !== undefined && !this.props.hideUploadMetadataBtn) {
      actions.push(
        roleModel.manager.entities(
          <Button
            size="small"
            onClick={this.openAddInstanceForm}
            key="add-metadata">
            <Icon type="plus" />Add instance
          </Button>,
          'add-metadata'
        )
      );
      actions.push(
        roleModel.manager.entities(
          <UploadButton
            key="upload-metadata"
            multiple={false}
            synchronous={true}
            onRefresh={async () => {
              await this.props.folder.fetch();
              if (this.props.onReloadTree) {
                this.props.onReloadTree(true);
              }
            }}
            title={'Upload metadata'}
            action={MetadataEntityUpload.uploadUrl(this.props.folderId)} />,
          'upload-metadata'
        )
      );
      actions.push(
        roleModel.manager.entities(
          <Button
            key="delete-metadata"
            type="danger"
            style={{lineHeight: 1}}
            onClick={this.deleteMetadataConfirm}
            size="small">
            <Icon type="delete" />
            Delete metadata
          </Button>,
          'delete-metadata'
        )
      );
    }
    return actions;
  };

  render () {
    const dataFolder = generateTreeData(this.props.folder.value, false);
    const [metadataFolder] = dataFolder.filter(m => m.type === ItemTypes.metadataFolder);
    let data = metadataFolder ? metadataFolder.children : [];
    if (this.props.folderId) {
      const url = `/folder/${this.props.folderId}`;
      data = [{
        id: this.props.folderId,
        name: '..',
        key: `${ItemTypes.folder}_${this.props.folderId}`,
        type: ItemTypes.folder,
        removable: false,
        url () {
          return url;
        }
      }, ...data];
    } else {
      data = [{
        id: undefined,
        name: '..',
        key: `${ItemTypes.folder}_root`,
        type: ItemTypes.folder,
        removable: false,
        url () {
          return '/library';
        }
      }, ...data];
    }
    this._currentFolder = {
      data: data,
      folder: this.props.folder.value
    };

    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row type="flex" justify="space-between" align="middle" style={{minHeight: 41}}>
          <Col className={styles.itemHeader}>
            <Icon type="appstore-o" className={styles.editableControl} />
            <Breadcrumbs
              id={parseInt(this.props.folderId)}
              type={ItemTypes.metadataFolder}
              textEditableField={'Metadata'}
              readOnlyEditableField={true}
            />
          </Col>
          <Col className={styles.currentFolderActions}>
            {this.renderActions()}
          </Col>
        </Row>
        {this.renderContent()}
        <AddInstanceForm
          folderId={this.props.folderId}
          visible={this.state.addInstanceFormVisible}
          pending={this.state.operationInProgress}
          onCreate={this.operationWrapper(this.addInstance)}
          onCancel={this.closeAddInstanceForm}
          entityTypes={this.entityTypes} />
      </div>
    );
  }

  componentDidMount () {
    if (this.props.selectionAvailable && !this.hasSelectionColumn) {
      const columns = [
        this.renderSelectionColumn(),
        ...this.state.columns
      ];
      this.setState({columns});
    }
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.initialSelection) {
      this.state.selectedItems = nextProps.initialSelection;
    }
    if (nextProps.folderId !== this.props.folderId) {
      this.state.selectedItems = [];
      if (nextProps.onSelectItem) {
        nextProps.onSelectItem(this.state.selectedItems);
      }
    }
    if (nextProps.selectionAvailable && !this.hasSelectionColumn) {
      const columns = [
        this.renderSelectionColumn(),
        ...this.state.columns
      ];
      this.setState({columns});
    } else if (!nextProps.selectionAvailable && this.hasSelectionColumn) {
      const columns = this.state.columns.slice(1);
      this.setState({columns});
    }
  }
}
