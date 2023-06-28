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
import {Checkbox, Modal, Table, Icon, Row, Col, Button, message, Alert} from 'antd';
import {ItemTypes, generateTreeData} from '../model/treeStructureFunctions';
import styles from './Browser.css';
import roleModel from '../../../utils/roleModel';
import folders from '../../../models/folders/Folders';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import MetadataEntityUpload from '../../../models/folderMetadata/MetadataEntityUpload';
import MetadataEntitySave from '../../../models/folderMetadata/MetadataEntitySave';
import MetadataClassLoadAll from '../../../models/folderMetadata/MetadataClassLoadAll';
import MetadataEntityDeleteFromProject
from '../../../models/folderMetadata/MetadataEntityDeleteFromProject';
import MetadataEntityFields from '../../../models/folderMetadata/MetadataEntityFields';
import UploadButton from '../../special/UploadButton';
import AddInstanceForm from './forms/AddInstanceForm';
import PropTypes from 'prop-types';
import Breadcrumbs from '../../special/Breadcrumbs';
import HiddenObjects from '../../../utils/hidden-objects';
import LoadingView from '../../special/LoadingView';

@connect({
  folders,
  pipelinesLibrary
})
@HiddenObjects.injectTreeFilter
@HiddenObjects.checkMetadataFolders(props => (props.params || props).id)
@inject(({folders, pipelinesLibrary}, params) => {
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    folder: componentParameters.id ? folders.load(componentParameters.id) : pipelinesLibrary,
    folderId: componentParameters.id,
    entityFields: new MetadataEntityFields(componentParameters.id),
    metadataClasses: new MetadataClassLoadAll(),
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
    selection: PropTypes.array
  };

  state = {
    selectedItems: [],
    addInstanceFormVisible: false,
    operationInProgress: false
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.folderId !== this.props.folderId) {
      this.clearSelection();
    } else if (prevProps.selection !== this.props.selection) {
      this.updateSelection();
    }
  }

  clearSelection = () => {
    this.setState({
      selectedItems: []
    }, () => {
      const {onSelectItem} = this.props;
      if (onSelectItem) {
        onSelectItem(this.state.selectedItems);
      }
    });
  };

  updateSelection = () => {
    const {selection = []} = this.props;
    this.setState({
      selectedItems: selection.slice()
    });
  };

  get parentFolderLinkItem () {
    const {folderId} = this.props;
    if (folderId) {
      const url = `/folder/${folderId}`;
      return {
        id: folderId,
        name: '..',
        key: `${ItemTypes.folder}_${folderId}`,
        type: ItemTypes.folder,
        removable: false,
        url () {
          return url;
        }
      };
    }
    return {
      id: undefined,
      name: '..',
      key: `${ItemTypes.folder}_root`,
      type: ItemTypes.folder,
      removable: false,
      url () {
        return '/library';
      }
    };
  }

  @computed
  get metadataFolderClassEntities () {
    const {folder} = this.props;
    if (folder && folder.loaded) {
      const dataFolder = generateTreeData(
        this.props.folder.value,
        {
          filter: this.props.hiddenObjectsTreeFilter()
        }
      );
      const metadataFolder = dataFolder.find(m => m.type === ItemTypes.metadataFolder);
      return metadataFolder ? metadataFolder.children : [];
    }
    return [];
  }

  get listingItems () {
    return [
      this.parentFolderLinkItem,
      ...this.metadataFolderClassEntities
    ];
  }

  @computed
  get entityTypes () {
    const {
      entityFields: entityFieldsRequest,
      metadataClasses
    } = this.props;
    if (
      entityFieldsRequest &&
      entityFieldsRequest.loaded &&
      metadataClasses &&
      metadataClasses.loaded
    ) {
      const entityFields = (entityFieldsRequest.value || []).map(e => e);
      const ignoreClasses = new Set(entityFields.map(f => f.metadataClass.id));
      const otherClasses = (metadataClasses.value || [])
        .filter(({id}) => !ignoreClasses.has(id))
        .map(metadataClass => ({
          fields: [],
          metadataClass: {...metadataClass, outOfProject: true}
        }));
      return [
        ...entityFields,
        ...otherClasses
      ];
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
    const {
      entityFields,
      folder,
      folderId,
      onReloadTree
    } = this.props;
    const classId = +values.entityClass;
    const [metadataClass] = this.entityTypes
      .map(e => e.metadataClass).filter(m => m.id === classId);
    if (metadataClass) {
      const className = metadataClass.name;
      const payload = {
        classId,
        className,
        externalId: values.id,
        data: values.data,
        parentId: folderId
      };
      const request = new MetadataEntitySave();
      await request.send(payload);
      if (request.error) {
        message.error(request.error, 5);
      } else {
        await entityFields.fetch();
        await folder.fetch();
        if (typeof onReloadTree === 'function') {
          const {parentId} = folder.loaded
            ? folder.value
            : {};
          onReloadTree(!parentId);
        }
        this.closeAddInstanceForm();
      }
    } else {
      message.error('Unknown metadata class', 5);
    }
  };

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
    } else if (item && typeof item.url === 'function') {
      this.props.router.push(item.url());
    }
  };

  selectMetadataClass = (metadataClass) => () => {
    const {selectedItems = []} = this.state;
    const newSelection = [...selectedItems];
    const selectedItemIndex = newSelection
      .findIndex(s => s.id === metadataClass.id && s.type === metadataClass.type);
    if (selectedItemIndex >= 0) {
      newSelection.splice(selectedItemIndex, 1);
    } else {
      newSelection.push(metadataClass);
    }
    this.setState({selectedItems: newSelection});
    if (this.props.onSelectItem) {
      this.props.onSelectItem(newSelection);
    }
  };

  renderContent = () => {
    const {
      selectionAvailable
    } = this.props;
    const {
      selectedItems = []
    } = this.state;
    const metadataClassIsSelected = (metadataClass) => {
      return !!selectedItems.find(selected => selected.id === metadataClass.id &&
        selected.type === metadataClass.type
      );
    };
    const selectionColumn = {
      key: 'selection',
      title: '',
      className: styles.checkboxCell,
      render: (item) => {
        if (item.type === ItemTypes.metadata) {
          return (
            <Checkbox
              disabled={selectedItems.length > 0 && !metadataClassIsSelected(item)}
              checked={metadataClassIsSelected(item)}
              onChange={this.selectMetadataClass(item)}
            />
          );
        } else {
          return <span />;
        }
      }
    };
    const columns = [
      selectionAvailable ? selectionColumn : undefined,
      {
        key: 'type',
        className: styles.treeItemType,
        render: (item) => this.renderTreeItemType(item),
        onCellClick: (item) => this.navigate(item)
      },
      {
        key: 'name',
        title: 'Name',
        className: styles.metadataFolderItemName,
        render: (item) => this.renderItemName(item),
        onCellClick: (item) => this.navigate(item)
      }
    ].filter(Boolean);
    return (
      <Row style={{padding: 5, overflowY: 'auto'}}>
        <Table
          className={styles.childrenContainer}
          dataSource={this.listingItems}
          columns={columns}
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
            disabled={this.entityTypes.length === 0}
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
            synchronous
            onRefresh={async () => {
              await this.props.entityFields.fetch();
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
    const {folderId, folder} = this.props;
    if (!folder) {
      return null;
    }
    if (folder.error) {
      return (<Alert message={folder.error} type="error" />);
    }
    if (folder.pending && !folder.loaded) {
      return (<LoadingView />);
    }
    const {
      addInstanceFormVisible,
      operationInProgress
    } = this.state;
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row type="flex" justify="space-between" align="middle" style={{minHeight: 41}}>
          <Col className={styles.itemHeader}>
            <Breadcrumbs
              id={parseInt(folderId)}
              type={ItemTypes.metadataFolder}
              textEditableField={'Metadata'}
              readOnlyEditableField
              icon="appstore-o"
              iconClassName={styles.editableControl}
              subject={folder.value}
              onNavigate={this.navigate}
            />
          </Col>
          <Col className={styles.currentFolderActions}>
            {this.renderActions()}
          </Col>
        </Row>
        {this.renderContent()}
        <AddInstanceForm
          folderId={folderId}
          visible={addInstanceFormVisible}
          pending={operationInProgress}
          onCreate={this.operationWrapper(this.addInstance)}
          onCancel={this.closeAddInstanceForm}
          entityTypes={this.entityTypes} />
      </div>
    );
  }
}
