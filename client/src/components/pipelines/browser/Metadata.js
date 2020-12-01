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
import {computed, observable} from 'mobx';
import connect from '../../../utils/connect';
import folders from '../../../models/folders/Folders';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import MetadataEntityFilter from '../../../models/folderMetadata/MetadataEntityFilter';
import MetadataEntityKeys from '../../../models/folderMetadata/MetadataEntityKeys';
import MetadataEntitySave from '../../../models/folderMetadata/MetadataEntitySave';
import MetadataEntityLoadExternal from '../../../models/folderMetadata/MetadataEntityLoadExternal';
import {Button, Checkbox, Col, Icon, Input, message, Modal, Pagination, Row} from 'antd';
import {
  ContentMetadataPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY
} from '../../special/splitPanel/SplitPanel';
import styles from './Browser.css';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import MetadataPanel from '../../special/metadataPanel/MetadataPanel';
import DropdownWithMultiselect from '../../special/DropdownWithMultiselect';
import AdaptedLink from '../../special/AdaptedLink';
import MetadataEntityFields from '../../../models/folderMetadata/MetadataEntityFields';
import UploadButton from '../../special/UploadButton';
import AddInstanceForm from './forms/AddInstanceForm';
import UploadToDatastorageForm from './forms/UploadToDatastorageForm';
import 'react-table/react-table.css';
import ReactTable from 'react-table';
import roleModel from '../../../utils/roleModel';
import MetadataEntityUpload from '../../../models/folderMetadata/MetadataEntityUpload';
import PropTypes from 'prop-types';
import MetadataClassLoadAll from '../../../models/folderMetadata/MetadataClassLoadAll';
import MetadataEntityDeleteFromProject
  from '../../../models/folderMetadata/MetadataEntityDeleteFromProject';
import MetadataEntityDeleteList from '../../../models/folderMetadata/MetadataEntityDeleteList';
import ConfigurationBrowser from '../launch/dialogs/ConfigurationBrowser';
import FolderProject from '../../../models/folders/FolderProject';
import ConfigurationRun from '../../../models/configuration/ConfigurationRun';
import PipelineRunner from '../../../models/pipelines/PipelineRunner';
import {ItemTypes} from '../model/treeStructureFunctions';
import Breadcrumbs from '../../special/Breadcrumbs';
import displayDate from '../../../utils/displayDate';

const PAGE_SIZE = 20;
const ASCEND = 'ascend';
const DESCEND = 'descend';

function filterColumns (column) {
  return !column.predefined || ['externalId', 'createdDate'].indexOf(column.name) >= 0;
}

function mapColumnName (column) {
  if (column.name === 'externalId') {
    return 'ID';
  }
  return column.name;
}

function unmapColumnName (name) {
  if (name === 'ID') {
    return 'externalId';
  }
  return name;
}

function getColumnTitle (key) {
  if (key === 'createdDate') {
    return 'Created Date';
  }
  return key;
}

@connect({
  folders,
  pipelinesLibrary
})
@inject('preferences', 'dataStorages')
@inject(({folders, pipelinesLibrary, authenticatedUserInfo, preferences, dataStorages}, params) => {
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    folder: componentParameters.id ? folders.load(componentParameters.id) : pipelinesLibrary,
    folderId: componentParameters.id,
    metadataClass: componentParameters.class,
    entityFields: new MetadataEntityFields(componentParameters.id),
    metadataClasses: new MetadataClassLoadAll(),
    onReloadTree: params.onReloadTree,
    authenticatedUserInfo,
    preferences,
    dataStorages
  };
})
@observer
export default class Metadata extends React.Component {

  static propTypes = {
    onSelectItems: PropTypes.func,
    initialSelection: PropTypes.array,
    hideUploadMetadataBtn: PropTypes.bool,
    readOnly: PropTypes.bool
  };

  _currentMetadata = [];
  _totalCount = 0;

  columns = [];
  defaultColumns = [];
  @observable keys;

  metadataRequest = {};
  externalMetadataEntity = {};

  selectedConfiguration = null;
  expansionExpression = '';

  state = {
    loading: false,
    metadata: false,
    selectedItem: null,
    selectedItems: this.props.initialSelection ? this.props.initialSelection : [],
    selectedColumns: [],
    filterModel: {
      filters: [],
      folderId: parseInt(this.props.folderId),
      metadataClass: this.props.metadataClass,
      orderBy: [],
      page: 1,
      pageSize: PAGE_SIZE,
      searchQueries: []
    },
    addInstanceFormVisible: false,
    operationInProgress: false,
    configurationBrowserVisible: false,
    currentProjectId: null,
    currentMetadataEntityForCurrentProject: [],
    uploadToBucketVisible: false
  };

  @computed
  get entityTypes () {
    if (this.props.entityFields.loaded && this.props.metadataClasses.loaded) {
      const entityFields = (this.props.entityFields.value || [])
        .map(e => e);
      const ignoreClasses = new Set(entityFields.map(f => f.metadataClass.id));
      const otherClasses = (this.props.metadataClasses.value || [])
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

  @computed
  get transferJobId () {
    if (this.props.preferences.loaded) {
      return +this.props.preferences.getPreferenceValue('storage.transfer.pipeline.id');
    }
    return null;
  }

  @computed
  get transferJobVersion () {
    if (this.props.preferences.loaded) {
      return this.props.preferences.getPreferenceValue('storage.transfer.pipeline.version');
    }
    return null;
  }

  @computed
  get currentClassEntityFields () {
    if (!this.keys) {
      return [];
    }
    const ownKeys = this.keys.filter(k => !k.predefined).map(k => k.name);
    const [metadata] = this.entityTypes
      .filter(e => e.metadataClass.name.toLowerCase() === (this.props.metadataClass || '').toLowerCase());
    if (metadata) {
      return (metadata.fields || [])
        .filter(f => ownKeys.indexOf(f.name) >= 0)
        .map(f => f);
    }
    return [];
  }

  @computed
  get currentClassEntityPathFields () {
    if (!this.keys) {
      return [];
    }
    const ownKeys = this.keys.filter(k => !k.predefined).map(k => k.name);
    const [metadata] = this.entityTypes
      .filter(e => e.metadataClass.name.toLowerCase() === (this.props.metadataClass || '').toLowerCase());
    if (metadata) {
      return (metadata.fields || [])
        .filter(f => f.type.toLowerCase() === 'path' && ownKeys.indexOf(f.name) >= 0)
        .map(f => f);
    }
    return [];
  }

  @computed
  get currentMetadataClassId () {
    const [metadataClassObj] = this.entityTypes
      .map(e => e.metadataClass)
      .filter(e => e.name.toLowerCase() === (this.props.metadataClass || '').toLowerCase());
    if (metadataClassObj) {
      return metadataClassObj.id;
    }
    return null;
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
    const [metadataClass] = this.entityTypes
      .map(e => e.metadataClass).filter(m => m.id === classId);
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
        await this.props.entityFields.fetch();
        await this.loadColumns(this.props.folderId, this.props.metadataClass);
        await this.loadData(this.state.filterModel);
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

  loadData = async (filterModel) => {
    this.setState({loading: true});
    this.metadataRequest = new MetadataEntityFilter();
    let orderBy;
    if (filterModel) {
      orderBy = (filterModel.orderBy || [])
        .map(o => ({...o, field: unmapColumnName(o.field)}));
    }
    await this.metadataRequest.send(Object.assign({...filterModel}, {orderBy}));
    if (this.metadataRequest.error) {
      message.error(this.metadataRequest.error, 5);
      this._currentMetadata = [];
    } else {
      if (this.metadataRequest.value) {
        this._totalCount = this.metadataRequest.value.totalCount;
        if (!this.state.filterModel.searchQueries.length) {
          const parentFolderId = this.props.folderId;
          if (this._totalCount <= 0) {
            this.props.router.push(`/folder/${parentFolderId}`);
            return;
          }
        }
        this._currentMetadata = (this.metadataRequest.value.elements || []).map(v => {
          v.data = v.data || {};
          v.data.rowKey = {
            value: v.id,
            type: 'string'
          };
          v.data.ID = {
            value: v.externalId,
            type: 'string'
          };
          v.data.createdDate = {
            value: v.createdDate,
            type: 'date'
          };
          return v.data;
        });
      }
    }
    this.setState({loading: false});
  };

  renderDataStorageLinks = (data) => {
    const urls = [];
    let title = '';
    if (data.dataStorageLinks) {
      for (let i = 0; i < data.dataStorageLinks.length; i++) {
        const link = data.dataStorageLinks[i];
        let url = `/storage/${link.dataStorageId}`;
        const parts = (link.absolutePath || link.path).split('/');
        const name = parts[parts.length - 1];
        title = `${title} ${name}`;
        if (link.path && link.path.length) {
          url = `/storage/${link.dataStorageId}?path=${link.path}`;
        }
        urls.push((
          <AdaptedLink
            key={i}
            to={url}
            location={this.props.router ? this.props.router.location : {}}>{name}</AdaptedLink>
        ));
      }
      return <span title={title}>{urls.map((url, index) => {
        return (
          <Row key={index}>
            {url}
          </Row>
        );
      })}</span>;
    }
    return <span title={data.value}>{data.value}</span>;
  };
  onDeleteSelectedItems = () => {
    const removeConfiguration = async () => {
      const selectedIds = this.state.selectedItems.map(item => item.rowKey.value);
      const request = new MetadataEntityDeleteList();
      await request.send(selectedIds);
      if (request.error) {
        message.error(request.error, 5);
      }
      this.setState({selectedItems: [], selectedItem: null, metadata: false});
      await this.loadData(this.state.filterModel);
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(true);
      }
    };
    Modal.confirm({
      title: 'Are you sure you want to remove all selected items?',
      style: {
        wordWrap: 'break-word'
      },
      async onOk () {
        await removeConfiguration();
      },
      okText: 'Yes',
      cancelText: 'No'
    });
  };

  loadColumns = async (folderId, metadataClass) => {
    this.columns = [];
    this.setState({loading: true});
    const metadataEntityKeysRequest =
      new MetadataEntityKeys(folderId, metadataClass);
    await metadataEntityKeysRequest.fetch();
    if (metadataEntityKeysRequest.error) {
      message.error(metadataEntityKeysRequest.error, 5);
    } else {
      this.keys = (metadataEntityKeysRequest.value || []).map(k => k);
      const externalIdSort = (a, b) => {
        if (a.name === b.name) {
          return 0;
        }
        if (a.name === 'externalId') {
          return -1;
        }
        if (b.name === 'externalId') {
          return 1;
        }
        return 0;
      };
      const predefinedSort = (a, b) => b.predefined - a.predefined;
      const newColumns = (metadataEntityKeysRequest.value || [])
        .sort(externalIdSort)
        .sort(predefinedSort)
        .filter(filterColumns)
        .map(mapColumnName);

      if (this.defaultColumns && this.defaultColumns.length < newColumns.length) {
        const addedColumns = newColumns.filter(column => !this.defaultColumns.includes(column));
        this.state.selectedColumns.push(...addedColumns);
        this.setState({selectedColumns: this.state.selectedColumns});
      }
      this.defaultColumns = this.columns = newColumns;
    }
  };

  onArrayReferencesClick = (event, key, data) => {
    const selectedItem = {};
    selectedItem[key] = {type: data.type, value: data.value};
    this.setState({metadata: true, selectedItem: selectedItem});
    event.stopPropagation();
  };

  onReferenceTypesClick = async (event, data) => {
    event.stopPropagation();
    const metadataEntityLoadExternalRequest =
      new MetadataEntityLoadExternal(data.value, data.type.split(':ID')[0], this.props.folderId);
    await metadataEntityLoadExternalRequest.fetch();
    if (metadataEntityLoadExternalRequest.error) {
      message.error(metadataEntityLoadExternalRequest.error, 5);
    } else {
      const selectedItem = metadataEntityLoadExternalRequest.value.data || {};
      this.externalMetadataEntity = metadataEntityLoadExternalRequest.value || {};

      selectedItem.rowKey = {
        value: metadataEntityLoadExternalRequest.value.id,
        type: 'string'
      };
      selectedItem.ID = {
        value: metadataEntityLoadExternalRequest.value.externalId,
        type: 'string'
      };
      selectedItem.createdDate = {
        value: metadataEntityLoadExternalRequest.value.createdDate,
        type: 'date'
      };
      this.setState({metadata: true, selectedItem: selectedItem});
    }
  };

  onSearchQueriesChanged = async () => {
    await this.loadData(this.state.filterModel);
  };

  onOrderByChanged = async (key, value) => {
    if (key) {
      const filterModel = this.state.filterModel;
      const [currentOrderBy] = filterModel.orderBy.filter(f => f.field === key);
      if (currentOrderBy) {
        const index = filterModel.orderBy.indexOf(currentOrderBy);
        filterModel.orderBy.splice(index, 1);
      }
      if (value) {
        filterModel.orderBy = [{field: key, desc: value === DESCEND}];
      }
      await this.loadData(this.state.filterModel);
    }
  };

  itemIsSelected = (item) => {
    return this.state.selectedItems.filter(s => s.rowKey.value === item.rowKey.value).length === 1;
  };

  onItemSelect = (item) => {
    const selectedItems = this.state.selectedItems;
    const [selectedItem] =
      this.state.selectedItems.filter(s => s.rowKey.value === item.rowKey.value);
    if (selectedItem) {
      const index = selectedItems.indexOf(selectedItem);
      selectedItems.splice(index, 1);
    } else {
      selectedItems.push(item);
    }
    this.setState({selectedItems});
    if (this.props.onSelectItems) {
      this.props.onSelectItems(selectedItems);
    }
  };

  onColumnSelect = (item) => {
    const selectedColumns = this.state.selectedColumns;
    const index = selectedColumns.indexOf(item);
    if (index !== -1) {
      selectedColumns.splice(index, 1);
    } else {
      selectedColumns.push(item);
    }
    this.setState({selectedColumns});
  };

  onResetColums = () => {
    this.columns = [...this.defaultColumns];
    this.setState({selectedColumns: [...this.defaultColumns]});
  };

  onSetOrder = (order) => {
    this.columns = order;
    this.forceUpdate();
  };

  onRowClick = (item) => {
    const [selectedItem] =
      this._currentMetadata.filter(column => column.rowKey === item.rowKey);
    if (this.state.selectedItem && this.state.selectedItem.rowKey === selectedItem.rowKey) {
      this.setState({selectedItem: null, metadata: false});
    } else {
      this.setState({selectedItem: selectedItem, metadata: true});
    }
  };
  onClearSelectionItems = () => {
    this.setState({selectedItems: []});
  };
  onSelectConfigurationConfirm = async (selectedConfiguration, expansionExpression) => {
    this.selectedConfiguration = selectedConfiguration;
    this.expansionExpression = expansionExpression;

    await this.runConfiguration(false);
  };
  onCloseConfigurationBrowser = () => {
    this.selectedConfiguration = null;
    this.expansionExpression = '';

    this.setState({
      configurationBrowserVisible: false
    });
  };

  onOpenUploadToBucketDialog = () => {
    this.setState({
      uploadToBucketVisible: true
    });
  };

  onCloseUploadToBucketDialog = () => {
    this.setState({
      uploadToBucketVisible: false
    });
  };

  onStartUploadToBucket = async (values) => {
    await this.props.dataStorages.fetchIfNeededOrWait();
    let {destination} = values;
    if (!destination.endsWith('/')) {
      destination = destination + '/';
    }
    destination = destination.toLowerCase();
    const [dataStorage] = (this.props.dataStorages.value || []).filter(dS => {
      let pathMask = (dS.pathMask || '').toLowerCase();
      if (!pathMask.endsWith('/')) {
        pathMask = pathMask + '/';
      }
      return destination === pathMask || destination.startsWith(pathMask);
    });
    const hide = message.loading('Starting transfer job...', -1);
    const getParam = (value, type = 'string', required = true) => {
      return {
        value,
        type,
        required
      };
    };
    const payload = {
      cloudRegionId: dataStorage ? dataStorage.regionId : undefined,
      pipelineId: this.transferJobId,
      version: this.transferJobVersion,
      params: {
        DESTINATION_DIRECTORY: getParam(values.destination),
        METADATA_ID: getParam(this.props.folderId),
        METADATA_CLASS: getParam(this.props.metadataClass),
        METADATA_COLUMNS: getParam((values.pathFields || []).join(',')),
        METADATA_ENTITIES: this.state.selectedItems.length > 0
          ? getParam(
            this.state.selectedItems.map(item => item.rowKey.value).join(','),
            'string',
            false)
          : undefined,
        FILE_NAME_FORMAT_COLUMN: getParam(values.nameField, 'string', false),
        CREATE_FOLDERS_FOR_COLUMNS: getParam(values.createFolders, 'boolean', false),
        UPDATE_PATH_VALUES: getParam(values.updatePathValues, 'boolean', false),
        MAX_THREADS_COUNT: values.threadsCount ? getParam(values.threadsCount, 'string', false) : undefined
      }
    };
    await PipelineRunner.send({...payload, force: true});
    hide();
    if (PipelineRunner.error) {
      message.error(PipelineRunner.error, 5);
    } else {
      SessionStorageWrapper.navigateToActiveRuns(this.props.router);
    }
  };

  loadCurrentProject = async () => {
    const folderProjectRequest =
      new FolderProject(this.props.folderId, 'FOLDER');
    await folderProjectRequest.fetch();
    if (folderProjectRequest.error) {
      message.error(folderProjectRequest.error, 5);
    } else {
      if (folderProjectRequest.value) {
        const currentProjectId = folderProjectRequest.value.id;
        const metadataEntityFieldsRequest =
          new MetadataEntityFields(currentProjectId);
        await metadataEntityFieldsRequest.fetch();
        if (metadataEntityFieldsRequest.error) {
          message.error(metadataEntityFieldsRequest.error, 5);
        } else {
          const currentMetadataEntityForCurrentProject = metadataEntityFieldsRequest.value || [];
          this.setState({
            currentMetadataEntityForCurrentProject,
            currentProjectId
          });
        }
      }
    }
  };
  runConfiguration = async (isCluster) => {
    const hide = message.loading('Launching...', 0);
    const request = new ConfigurationRun(this.expansionExpression);
    await request.send({
      id: this.selectedConfiguration ? this.selectedConfiguration.id : null,
      entries: isCluster
        ? this.selectedConfiguration.entries
        : this.selectedConfiguration.entries.filter(entry => entry.default),
      entitiesIds: this.state.selectedItems.map(item => item.rowKey.value),
      metadataClass: this.props.metadataClass,
      folderId: parseInt(this.props.folderId)
    });
    hide();
    this.setState({
      configurationBrowserVisible: false
    });
    if (request.error) {
      message.error(request.error);
    } else {
      SessionStorageWrapper.navigateToActiveRuns(this.props.router);
    }
  };
  renderContent = () => {
    const renderTable = () => {
      return [
        <ReactTable
          key="table"
          className={`${styles.metadataTable} -striped -highlight`}
          sortable={false}
          minRows={0}
          columns={this.tableColumns}
          data={this._currentMetadata}
          getTableProps={() => ({style: {overflowY: 'hidden'}})}
          getTdProps={(state, rowInfo, column, instance) => ({
            onClick: (e) => {
              if (e) {
                e.stopPropagation();
              }
              if (column.id === 'selection') {
                this.onItemSelect(rowInfo.row._original);
              } else {
                this.onRowClick(rowInfo.row._original);
              }
            }
          })}
          PadRowComponent={
            () =>
              <div className={styles.metadataColumnCell}>
                <span>{'\u00A0'}</span>
              </div>
          }
          showPagination={false} />,
        <Row key="pagination" type="flex" justify="end" style={{marginTop: 10}}>
          <Pagination
            size="small"
            pageSize={PAGE_SIZE}
            current={this.state.filterModel.page}
            total={this._totalCount}
            onChange={
              async (page) => {
                this.state.filterModel.page = page;
                await this.loadData(this.state.filterModel);
                this.setState({filterModel: this.state.filterModel});
              }
            } />
        </Row>
      ];
    };

    const renderConfigurationBrowser = () => {
      return this.state.currentProjectId ? (
        <Row>
          <ConfigurationBrowser
            onCancel={this.onCloseConfigurationBrowser}
            onSelect={this.onSelectConfigurationConfirm}
            visible={this.state.configurationBrowserVisible}
            initialFolderId={this.state.currentProjectId}
            currentMetadataEntity={
              this.state.currentMetadataEntityForCurrentProject.map(entity => entity)
            }
            metadataClassName={this.props.metadataClass}
          />
        </Row>
      ) : null;
    };

    const onPanelClose = (key) => {
      switch (key) {
        case METADATA_PANEL_KEY:
          this.setState({metadata: false});
          break;
      }
    };

    const allMetadata = [
      ...((this.metadataRequest.value && this.metadataRequest.value.elements) || []),
      this.externalMetadataEntity
    ];
    const [currentItem] = this.state.selectedItem && this.state.selectedItem.rowKey
      ? allMetadata.filter(metadata => metadata.id === this.state.selectedItem.rowKey.value)
      : [];

    return (
      <ContentMetadataPanel
        style={{flex: 1, overflow: 'auto'}}
        onPanelClose={onPanelClose}>
        <div key={CONTENT_PANEL_KEY}>
          {renderTable()}
          {renderConfigurationBrowser()}
        </div>
        {
          this.state.metadata &&
          <MetadataPanel
            key={METADATA_PANEL_KEY}
            readOnly={!(roleModel.writeAllowed(this.props.folder.value) &&
            this.props.folderId !== undefined)}
            readOnlyKeys={['ID', 'createdDate']}
            columnNamesFn={getColumnTitle}
            classId={currentItem ? currentItem.classEntity.id : null}
            className={currentItem ? currentItem.classEntity.name : null}
            entityId={currentItem ? currentItem.id : null}
            entityName={''}
            externalId={currentItem ? currentItem.externalId : null}
            parentId={currentItem ? currentItem.parent.id : null}
            currentItem={this.state.selectedItem}
            onUpdateMetadata={async () => {
              await this.props.entityFields.fetch();
              await this.loadColumns(this.props.folderId, this.props.metadataClass);
              await this.loadData(this.state.filterModel);
              const [selectedItem] =
                this._currentMetadata
                  .filter(metadata => metadata.rowKey.value === currentItem.id);
              this.setState({selectedItem: selectedItem});
              await this.props.folder.fetch();
              if (this.props.onReloadTree) {
                this.props.onReloadTree(true);
              }
            }}
          />
        }
      </ContentMetadataPanel>
    );
  };

  deleteMetadataClassConfirm = () => {
    const onDeleteMetadataClass = async () => {
      const hide = message.loading(`Removing class '${this.props.metadataClass}'...`, -1);
      const request = new MetadataEntityDeleteFromProject(this.props.folderId, this.props.metadataClass);
      await request.fetch();
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.folder.fetch();
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this.props.folder.value.parentId);
        }
        hide();
        this.props.router.push(`/metadataFolder/${this.props.folderId}`);
      }
    };
    Modal.confirm({
      title: `Delete class '${this.props.metadataClass}'?`,
      onOk: onDeleteMetadataClass
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
            onClick={this.deleteMetadataClassConfirm}
            size="small">
            <Icon type="delete" />
            Delete class
          </Button>,
          'delete-metadata'
        )
      );
    }
    actions.push(
      <Button
        key="metadata"
        id={this.state.metadata ? 'hide-metadata-button' : 'show-metadata-button'}
        style={{marginRight: 10}}
        size="small"
        onClick={() => this.setState({metadata: !this.state.metadata})}>
        {
          this.state.metadata ? 'Hide attributes' : 'Show attributes'
        }
      </Button>
    );
    return actions.filter(action => !!action);
  };

  get tableColumns () {
    const onHeaderClicked = (key, e) => {
      if (e) {
        e.stopPropagation();
      }
      const [orderBy] = this.state.filterModel.orderBy.filter(f => f.field === key);
      if (!orderBy) {
        this.onOrderByChanged(key, DESCEND);
      } else if (orderBy.desc) {
        this.onOrderByChanged(key, ASCEND);
      } else {
        this.onOrderByChanged(key);
      }
    };
    const renderTitle = (key) => {
      const [orderBy] = this.state.filterModel.orderBy.filter(f => f.field === key);
      let icon;
      if (orderBy) {
        if (orderBy.desc) {
          icon = <Icon style={{fontSize: 10, marginRight: 5}} type="caret-down" />;
        } else {
          icon = <Icon style={{fontSize: 10, marginRight: 5}} type="caret-up" />;
        }
      }
      return (
        <span
          onClick={(e) => onHeaderClicked(key)}
          className={styles.metadataColumnHeader}>
          {icon}{getColumnTitle(key)}
        </span>
      );
    };
    const getCellClassName = (item, defaultClass) => {
      let selected = false;
      if (this.state.selectedItem &&
        this.state.selectedItem.rowKey &&
        this.state.selectedItem.rowKey.value === item.rowKey.value) {
        selected = true;
      }
      if (selected) {
        return `${defaultClass} ${styles.selected}`;
      }
      return defaultClass;
    };
    const cellWrapper = (props, reactElementFn) => {
      const item = props.original;
      const className = getCellClassName(item, styles.metadataColumnCell);
      return (
        <div className={className} style={{overflow: 'hidden', textOverflow: 'ellipsis'}} >
          {reactElementFn()}
        </div>
      );
    };
    const renderAdditionalActions = () => {
      if (roleModel.writeAllowed(this.props.folder.value) && !this.props.readOnly &&
        roleModel.isManager.entities(this)) {
        return (
          <Row type="flex" justify="space-between">
            <Col style={{padding: 3, textAlign: 'left'}}>
              {
                this.state.selectedItems &&
                this.state.selectedItems.length > 0 &&
                <span> Selected {this.state.selectedItems ? this.state.selectedItems.length : 0} items </span>
              }
            </Col>
            <Col>
              <Row style={{paddingRight: 5}} className={styles.currentFolderActions}>
                <Button
                  key="delete"
                  size="small"
                  type="danger"
                  disabled={!this.state.selectedItems || this.state.selectedItems.length === 0}
                  onClick={this.onDeleteSelectedItems}>
                  DELETE
                </Button>
                <Button
                  key="clear_selection"
                  size="small"
                  disabled={!this.state.selectedItems || this.state.selectedItems.length === 0}
                  onClick={this.onClearSelectionItems}>
                  CLEAR SELECTION
                </Button>
                {
                  this.transferJobId && this.transferJobVersion && this.currentClassEntityPathFields.length > 0 &&
                  <Button
                    key="download_selection"
                    size="small"
                    type="primary"
                    onClick={this.onOpenUploadToBucketDialog}>
                    TRANSFER TO THE CLOUD
                  </Button>
                }
                <Button
                  key="run"
                  size="small"
                  type="primary"
                  disabled={!this.state.selectedItems || this.state.selectedItems.length === 0}
                  onClick={() => this.setState({configurationBrowserVisible: true})}>
                  RUN
                </Button>
              </Row>
            </Col>
          </Row>
        );
      } else {
        return null;
      }
    };

    const allColumns = [
      {
        id: 'selection',
        accessor: item => item,
        Header: '',
        resizable: false,
        width: 30,
        style: {
          cursor: 'pointer',
          padding: 0,
          borderRight: '1px solid rgba(0, 0, 0, 0.1)'
        },
        className: styles.metadataCheckboxCell,
        Cell: (props) => cellWrapper(props, () => {
          const item = props.value;
          return (
            <Checkbox
              style={{borderCollapse: 'separate'}}
              checked={this.itemIsSelected(item)}
            />
          );
        })
      },
      ...this.columns.filter(c => this.state.selectedColumns.indexOf(c) >= 0).map(key => {
        return {
          accessor: key,
          style: {
            cursor: 'pointer',
            padding: 0,
            borderRight: '1px solid rgba(0, 0, 0, 0.1)'
          },
          Header: () => renderTitle(key),
          Cell: props => cellWrapper(props, () => {
            const data = props.value;
            if (data) {
              if (data.type.toLowerCase().startsWith('array')) {
                let referenceType = key;
                const regex = /array\[(.*)\]/i;
                const matchResult = data.type.match(regex);
                if (matchResult && matchResult.length > 1) {
                  referenceType = matchResult[1] || key;
                }
                let count = 0;
                try {
                  count = JSON.parse(data.value).length;
                } catch (___) {}
                let value = `${count} ${referenceType}(s)`;
                return <a
                  title={value}
                  className={styles.actionLink}
                  onClick={(e) => this.onArrayReferencesClick(e, key, data)}>
                  {value}
                </a>;
              } else if (data.type.toLowerCase().endsWith(':id')) {
                return <a
                  title={data.value}
                  className={styles.actionLink}
                  onClick={(e) => this.onReferenceTypesClick(e, data)}>
                  {data.value}
                </a>;
              } else if (data.type.toLowerCase() === 'path') {
                return this.renderDataStorageLinks(data);
              } else if (/^date$/i.test(data.type)) {
                return (
                  <span title={data.value}>
                    {displayDate(data.value)}
                  </span>
                );
              } else {
                return (
                  <span title={data.value}>
                    {data.value}
                  </span>
                );
              }
            }
          })
        };
      })];

    if (this.props.readOnly) {
      return allColumns;
    } else {
      return [{
        id: 'title',
        headerClassName: styles.metadataAdditionalActions,
        Header: () => renderAdditionalActions(),
        columns: allColumns
      }];
    }
  };

  render () {
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          style={{minHeight: 41, marginBottom: 6}}>
          <Col>
            <span className={styles.itemHeader}>
              <Breadcrumbs
                id={parseInt(this.props.folderId)}
                type={ItemTypes.metadata}
                textEditableField={this.props.metadataClass}
                readOnlyEditableField={true}
                icon="appstore-o"
                iconClassName={styles.editableControl}
                subject={this.props.folder.value}
              />
            </span>
            <span className={styles.searchControl}>
              <Input.Search
                id="search-metadata-input"
                placeholder="Search"
                value={this.state.filterModel.searchQueries[0]}
                onPressEnter={this.onSearchQueriesChanged}
                onChange={(e) => {
                  this.state.filterModel.searchQueries = [e.target.value.trim()];
                  this.setState({filterModel: this.state.filterModel});
                }}
              />
              <DropdownWithMultiselect
                onColumnSelect={this.onColumnSelect}
                onSetOrder={this.onSetOrder}
                selectedColumns={this.state.selectedColumns}
                columns={this.columns}
                onResetColums={this.onResetColums}
                columnNameFn={getColumnTitle}
              />
            </span>
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
          entityType={this.currentMetadataClassId}
          entityTypes={this.entityTypes} />
        <UploadToDatastorageForm
          visible={this.state.uploadToBucketVisible}
          fields={this.currentClassEntityFields.filter(f => f.type.toLowerCase() !== 'path').map(f => f.name)}
          pathFields={this.currentClassEntityPathFields.map(f => f.name)}
          onTransfer={this.onStartUploadToBucket}
          onClose={this.onCloseUploadToBucketDialog} />
      </div>
    );
  };

  componentDidMount () {
    (async () => {
      await this.loadColumns(this.props.folderId, this.props.metadataClass);
      this.state.selectedColumns = [...this.columns];
      await this.loadData(this.state.filterModel);
      await this.loadCurrentProject();
    })();
  };

  async componentWillReceiveProps (nextProps) {
    if (nextProps.initialSelection) {
      this.state.selectedItems = nextProps.initialSelection;
    }
    if (nextProps.folderId !== this.props.folderId ||
      nextProps.metadataClass !== this.props.metadataClass) {
      this.state.selectedItem = null;
      this.state.selectedItems = [];
      this.state.filterModel = {
        filters: [],
        folderId: parseInt(nextProps.folderId),
        metadataClass: nextProps.metadataClass,
        orderBy: [],
        page: 1,
        pageSize: PAGE_SIZE,
        searchQueries: []
      };
      if (nextProps.onSelectItems) {
        nextProps.onSelectItems(this.state.selectedItems);
      }
      this._totalCount = 0;
      await this.props.entityFields.fetch();
      await this.loadColumns(nextProps.folderId, nextProps.metadataClass);
      this.state.selectedColumns = [...this.columns];
      await this.loadData(this.state.filterModel);
    }
    if (nextProps.folderId !== this.props.folderId) {
      await this.loadCurrentProject();
    }
  };
}
