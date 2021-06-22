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
import {
  Button,
  Checkbox,
  Dropdown,
  Icon,
  Input,
  message,
  Menu,
  Modal,
  Pagination,
  Row
} from 'antd';
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
import CopyMetadataEntitiesDialog from './forms/CopyMetadataEntitiesDialog';
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
import HiddenObjects from '../../../utils/hidden-objects';
import RangeDatePicker from './metadata-controls/RangeDatePicker';
import FilterControl from './metadata-controls/FilterControl';
import parseSearchQuery from './metadata-controls/parse-search-query';

const FIRST_PAGE = 1;
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
@HiddenObjects.checkMetadataFolders(p => (p.params || p).id)
@HiddenObjects.checkMetadataClassesWithParent(p => (p.params || p).id, p => (p.params || p).class)
@inject(({folders, pipelinesLibrary, authenticatedUserInfo, preferences, dataStorages}, params) => {
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    folders,
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

  _totalCount = 0;

  columns = [];
  defaultColumns = [];
  @observable keys;
  dateKeys = [];

  metadataRequest = {};
  externalMetadataEntity = {};

  selectedConfiguration = null;
  expansionExpression = '';

  state = {
    loading: false,
    metadata: false,
    searchQuery: undefined,
    selectedItem: null,
    selectedItems: this.props.initialSelection ? this.props.initialSelection : [],
    selectedItemsCanBeSkipped: false,
    selectedItemsAreShowing: false,
    selectedColumns: [],
    selectionStart: null,
    selectionDirection: '',
    selectedCells: [],
    filterModel: {
      startDateFrom: undefined,
      endDateTo: undefined,
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
    uploadToBucketVisible: false,
    copyEntitiesDialogVisible: false,
    currentMetadata: []
  };

  uploadButton;

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
    const metadataClass = (this.props.metadataClass || '').toLowerCase();
    const [metadata] = this.entityTypes
      .filter(e => e.metadataClass.name.toLowerCase() === metadataClass);
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
    const metadataClass = (this.props.metadataClass || '').toLowerCase();
    const [metadata] = this.entityTypes
      .filter(e => e.metadataClass.name.toLowerCase() === metadataClass);
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

  onDateRangeChanged = async (range) => {
    let filterModel = {...this.state.filterModel};
    const {
      from,
      to
    } = range || {};
    filterModel.startDateFrom = from;
    filterModel.endDateTo = to;
    this.setState(
      {filterModel},
      () => this.loadData()
    );
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
        await this.loadData();
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

  handleFilterApplied = async (key, dataArray) => {
    const filterModel = {...this.state.filterModel};
    if (key && dataArray && dataArray.length) {
      const filterObj = {key: unmapColumnName(key), values: dataArray};
      const currentFilterIndex = filterModel.filters
        .findIndex(filter => filter.key === unmapColumnName(key));
      if (currentFilterIndex > -1) {
        filterModel.filters[currentFilterIndex] = filterObj;
      } else {
        filterModel.filters.push(filterObj);
      }
    } else {
      filterModel.filters = filterModel.filters.filter(obj => obj.key !== unmapColumnName(key));
    }
    filterModel.page = FIRST_PAGE;
    this.setState(
      {filterModel},
      () => this.loadData()
    );
  }

  loadData = async () => {
    this.setState({loading: true});
    this.metadataRequest = new MetadataEntityFilter();
    const {filterModel, selectedItemsAreShowing, selectedItems} = this.state;
    let orderBy, filters;
    let currentMetadata = [];
    if (filterModel) {
      orderBy = (filterModel.orderBy || [])
        .map(o => ({...o, field: unmapColumnName(o.field)}));
      filters = (filterModel.filters || [])
        .map(o => ({...o, field: unmapColumnName(o.field)}));
    }
    if (!selectedItemsAreShowing) {
      await this.metadataRequest.send(
        {
          ...filterModel,
          orderBy,
          filters
        }
      );
      if (this.metadataRequest.error) {
        message.error(this.metadataRequest.error, 5);
        currentMetadata = [];
      } else {
        if (this.metadataRequest.value) {
          this._totalCount = this.metadataRequest.value.totalCount;
          if (this.metadataRequest.value.elements && this.metadataRequest.value.elements.length) {
            this._classEntity = {
              id: this.metadataRequest.value.elements[0].classEntity.id,
              name: this.metadataRequest.value.elements[0].classEntity.name
            };
          }
          currentMetadata = (this.metadataRequest.value.elements || []).map(v => {
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
    } else {
      const {page, pageSize} = filterModel;
      this._totalCount = selectedItems.length;

      const firstRow = Math.max((page - 1) * pageSize, 0);
      const lastRow = Math.min(page * pageSize, selectedItems.length);

      if (orderBy && orderBy.length) {
        const field = orderBy[0].field === 'externalId' ? 'ID' : orderBy[0].field;
        const desc = orderBy[0].desc;
        selectedItems.sort((a, b) => {
          if (!desc) {
            return a[field].value >= b[field].value ? 1 : -1;
          } else {
            return a[field].value < b[field].value ? 1 : -1;
          }
        });
        currentMetadata = selectedItems.slice(firstRow, lastRow);
      } else {
        currentMetadata = this.state.selectedItems.slice(firstRow, lastRow);
      }
    }
    this.setState({loading: false, currentMetadata});
  };

  filterApplied = (key) => {
    const {filters, startDateFrom, endDateTo} = this.state.filterModel;
    if (key !== 'createdDate') {
      return filters
        .filter(filterObj => filterObj.key === unmapColumnName(key)).length;
    } else {
      return startDateFrom || endDateTo;
    }
  };

  renderFilterButton = (key) => {
    if (this.state.selectedItemsAreShowing) {
      return null;
    }
    const {filterModel = {}} = this.state;
    const {
      filters = [],
      startDateFrom,
      endDateTo
    } = filterModel;
    const filter = filters.find(filter => filter.key === unmapColumnName(key));
    const values = filter ? (filter.values || []) : [];
    const button = (
      <Button
        shape="circle"
        onClick={(e) => e.stopPropagation()}
        style={{
          marginLeft: 5,
          border: 'none',
          color: (
            this.filterApplied(key)
              ? '#108ee9' : 'grey'
          )
        }}
      >
        <Icon type="filter" />
      </Button>);

    if (key === 'createdDate') {
      return (
        <RangeDatePicker
          from={startDateFrom}
          to={endDateTo}
          onChange={(e) => this.onDateRangeChanged(e, key)}
        >
          {button}
        </RangeDatePicker>
      );
    }
    return (
      <FilterControl
        columnName={key}
        onSearch={(tags) => this.handleFilterApplied(key, tags)}
        value={values}
      >
        {button}
      </FilterControl>
    );
  }

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
      await this.loadData();
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
        this.setState({selectedColumns: [...this.state.selectedColumns, ...addedColumns]});
      }
      if (this.defaultColumns && this.defaultColumns.length === newColumns.length) {
        this.setState({selectedColumns: [...newColumns]});
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
      this.setState({metadata: true, selectedItem});
    }
  };

  onSearchQueriesChanged = async () => {
    const {filterModel, searchQuery} = this.state;
    const {
      folderId,
      metadataClass
    } = this.props;
    const {filters, searchQueries} = parseSearchQuery(searchQuery);
    const {orderBy = []} = filterModel || {};
    const newFilterModel = {
      startDateFrom: undefined,
      endDateTo: undefined,
      filters: filters.map(filter => ({
        ...filter,
        key: unmapColumnName(filter.key)
      })),
      folderId: parseInt(folderId),
      metadataClass: metadataClass,
      orderBy,
      page: 1,
      pageSize: PAGE_SIZE,
      searchQueries
    };
    this.setState({
      filterModel: newFilterModel,
      selectedItemsAreShowing: false
    }, () => this.loadData());
  };

  onOrderByChanged = async (key, value) => {
    if (key) {
      const {filterModel} = this.state;
      const [currentOrderBy] = filterModel.orderBy.filter(f => f.field === key);
      if (currentOrderBy) {
        const index = filterModel.orderBy.indexOf(currentOrderBy);
        filterModel.orderBy.splice(index, 1);
      }
      if (value) {
        filterModel.orderBy = [{field: key, desc: value === DESCEND}];
      }
      await this.loadData();
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
      if (selectedItems.length === 0) {
        this.setState(
          {selectedItemsAreShowing: false},
          () => this.paginationOnChange(FIRST_PAGE)
        );
      }
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

  onResetColumns = () => {
    this.columns = [...this.defaultColumns];
    this.setState({selectedColumns: [...this.defaultColumns]});
  };

  onSetOrder = (order) => {
    this.columns = order;
    this.forceUpdate();
  };

  onRowClick = (item) => {
    const [selectedItem] =
      this.state.currentMetadata.filter(column => column.rowKey === item.rowKey);
    if (this.state.selectedItem && this.state.selectedItem.rowKey === selectedItem.rowKey) {
      this.setState({selectedItem: null, metadata: false});
    } else {
      this.setState({selectedItem, metadata: true});
    }
  };
  onClearFilters = () => {
    const {filterModel} = this.state;
    filterModel.filters = [];
    filterModel.startDateFrom = undefined;
    filterModel.endDateTo = undefined;
    filterModel.page = 1;
    filterModel.searchQueries = [];
    this.setState(
      {
        filterModel,
        searchQuery: undefined
      },
      () => this.loadData()
    );
  };
  onClearSelectionItems = () => {
    this.setState({
      selectedItems: [],
      selectedItemsAreShowing: false
    }, () => this.paginationOnChange(FIRST_PAGE));
  };
  onCopySelectionItems = () => {
    this.setState({
      copyEntitiesDialogVisible: true
    });
  };
  onCloseCopySelectionItemsDialog = (destinationFolder) => {
    this.setState({
      copyEntitiesDialogVisible: false
    }, () => {
      if (destinationFolder) {
        this.loadData();
      }
      this.props.folders.invalidateFolder(this.props.folderId);
      this.props.folders.invalidateFolder(destinationFolder);
      if (this.props.onReloadTree) {
        this.props.onReloadTree(true);
      }
    });
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
        MAX_THREADS_COUNT: values.threadsCount
          ? getParam(values.threadsCount, 'string', false)
          : undefined
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
  isNowSelectedCell = (rIdx, cIdx) => {
    return this.state.selectedCells.find((cell) => cell.colIdx === cIdx && cell.rowIdx === rIdx);
  }
  // handleShowSelector = (opts) => {
  //   const {e, rowInfo, column} = opts;
  //   const {selectionStart} = this.state;
  //   if (e.target.className === 'selector') {
  //     if (!selectionStart) {
  //     e.target.style.backgroundColor = '#108ee9';
  //     e.currentTarget.style.boxShadow = '0px 0px 0px 3px #108ee9 inset';
  //     } else if (this.isNowSelectedCell(rowInfo.index, column.index)) {
  //       e.target.style.backgroundColor = '#108ee9';
  //   } else {
  //     e.target.style.backgroundColor = 'transparent';
  //   }
  // }
  // }
  // handleHideSelector = (opts) => {
  //   const {e} = opts;
  //   if (e.target.className === 'selector') {
  //     e.target.style.backgroundColor = 'transparent';
  //     e.currentTarget.style.boxShadow = 'none';
  //   }
  // }
  handleStartSelection = (opts) => {
    const {e, rowInfo, column} = opts;
    const cell = e ? e.currentTarget : null;
    this.setState({selectionStart: {
      cells: [cell],
      colIdx: column.index,
      rowIdx: rowInfo.index,
      value: cell ? cell.innerText : ''
    }});
  }
  handleApplySpreadSelection = () => {
    console.log('mouseup', this.state.selectedCells);
  }
  isLeftSideCell = (column) => {
    const {selectedCells} = this.state;
    if (selectedCells.length) {
      const leftColumnIndex = this.state.selectedCells.reduce((min, c, index) => {
        if (index === 0) {
          min = c.colIdx;
        } else {
          min = Math.min(min, c.colIdx);
        }
        return min;
      }, 0);
      console.log('leftColumnIndex', leftColumnIndex);
      return leftColumnIndex === column.index;
    }
  }
  isRightSideCell = (column) => {
    const {selectedCells} = this.state;
    if (selectedCells.length) {
      const rightColumnIndex = this.state.selectedCells.reduce((max, c, index) => {
        if (index === 0) {
          max = c.colIdx;
        } else {
          max = Math.max(max, c.colIdx);
        }
        return max;
      }, 0);
      console.log('rightColumnIndex', rightColumnIndex);
      return rightColumnIndex === column.index;
    }
  }
  isTopSideCell = (column, row) => {
    const {selectedCells} = this.state;
    if (selectedCells.length) {
      const topRowIndex = selectedCells.reduce((min, c, index) => {
        if (index === 0) {
          min = c.rowIdx;
        } else {
          min = Math.min(min, c.rowIdx);
        }
        return min;
      }, 0);
      return topRowIndex === row.index;
    }
  }
  isBottomSideCell = (column, row) => {
    const {selectedCells} = this.state;
    if (selectedCells.length) {
      const bottomColumnIndex = this.state.selectedCells.reduce((max, c, index) => {
        if (index === 0) {
          max = c.rowIdx;
        } else {
          max = Math.max(max, c.rowIdx);
        }
        return max;
      }, 0);
      return bottomColumnIndex === row.index;
    }
  }
  handleCellSelection = (opts) => {
    const {e, rowInfo, column} = opts;
    const cell = e.currentTarget;
    const rowIndex = rowInfo.index;
    const columnIndex = column.index;
    if (this.state.selectionStart) {
      if (!this.isNowSelectedCell(rowIndex, columnIndex)) {
        this.setState({
          selectedCells: [...this.state.selectedCells, {
            rowIdx: rowIndex,
            colIdx: columnIndex
          }]
        });
        cell.style.backgroundColor = 'rgba(16, 142, 233, 0.1)';
      }
    }
  }
  renderContent = () => {
    const renderTable = () => {
      return [
        <ReactTable
          key="table"
          className={`${styles.metadataTable} -striped -highlight`}
          sortable={false}
          minRows={0}
          columns={this.tableColumns}
          data={this.state.currentMetadata}
          getTableProps={() => ({style: {overflowY: 'hidden', userSelect: 'none', borderCollapse: 'collapse'}})}
          getTrGroupProps={() => ({style: {borderBottom: 'none'}})}
          getTdProps={(state, rowInfo, column, instance) => ({
            onMouseDown: (e) => this.handleStartSelection({e, rowInfo, column}),
            onMouseUp: () => this.handleApplySpreadSelection(),
            onMouseMove: (e) => this.handleCellSelection({e, rowInfo, column}),
            onClick: (e) => {
              if (e) {
                e.stopPropagation();
              }
              if (column.id === 'selection') {
                this.onItemSelect(rowInfo.row._original);
              } else {
                this.onRowClick(rowInfo.row._original);
              }
              this.setState({
                selectionStart: null,
                selectedCells: []
              });
            },
            style: {
              border: '0.5px solid rgba(0,0,0,0.1)',
              position: 'relative',
              borderTopColor: this.isTopSideCell(column, rowInfo) && this.isNowSelectedCell(rowInfo.index, column.index) ? '#108ee9' : 'rgba(0,0,0,0.1)',
              borderBottomColor: this.isBottomSideCell(column, rowInfo) && this.isNowSelectedCell(rowInfo.index, column.index) ? '#108ee9' : 'rgba(0,0,0,0.1)',
              borderLeftColor: this.isLeftSideCell(column) && this.isNowSelectedCell(rowInfo.index, column.index) ? '#108ee9' : 'rgba(0,0,0,0.1)',
              borderRightColor: this.isRightSideCell(column) && this.isNowSelectedCell(rowInfo.index, column.index) ? '#108ee9' : 'rgba(0,0,0,0.1)',
              backgroundColor: this.isNowSelectedCell(rowInfo.index, column.index) ? 'rgba(16, 142, 233, 0.1)' : 'initial'
            }
          })}
          getResizerProps={() => ({style: {width: '6px', right: '-3px'}})}
          PadRowComponent={
            () =>
              <div className={styles.metadataColumnCell}>
                <span>{'\u00A0'}</span>
              </div>
          }
          showPagination={false}
          NoDataComponent={() => <div className={`${styles.noData}`}>No rows found</div>} />,
        <Row key="pagination" type="flex" justify="end" style={{marginTop: 10}}>
          <Pagination
            size="small"
            pageSize={PAGE_SIZE}
            current={this.state.filterModel.page}
            total={this._totalCount}
            onChange={async (page) => this.paginationOnChange(page)} />
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

    const renderCopyEntitiesDialog = () => {
      return (
        <CopyMetadataEntitiesDialog
          entities={this.state.selectedItems}
          visible={this.state.copyEntitiesDialogVisible}
          onCancel={this.onCloseCopySelectionItemsDialog}
          onCopy={this.onCloseCopySelectionItemsDialog}
          metadataClass={this.props.metadataClass}
          folderId={this.props.folderId}
        />
      );
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
    let [currentItem] = this.state.selectedItem && this.state.selectedItem.rowKey
      ? allMetadata.filter(metadata => metadata.id === this.state.selectedItem.rowKey.value)
      : [];
    if (
      this.state.selectedItemsAreShowing &&
      this.state.selectedItem &&
      this.state.selectedItem.rowKey
    ) {
      [currentItem] = this.state.selectedItems
        .filter(item => item.rowKey.value === this.state.selectedItem.rowKey.value)
        .map(item => {
          item = {
            data: {...item},
            classEntity: {...this._classEntity},
            createdDate: item.createdDate.value,
            externalId: item.ID.value,
            id: item.rowKey.value,
            parent: {
              id: this.state.filterModel.folderId
            }
          };
          return item;
        });
    }

    return (
      <ContentMetadataPanel
        style={{flex: 1, overflow: 'auto'}}
        onPanelClose={onPanelClose}>
        <div key={CONTENT_PANEL_KEY}>
          {this.renderTableActions()}
          {renderTable()}
          {renderConfigurationBrowser()}
          {renderCopyEntitiesDialog()}
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
              await this.loadData();
              const [selectedItem] =
                this.state.currentMetadata
                  .filter(metadata => metadata.rowKey.value === currentItem.id);
              if (this.state.selectedItems && this.state.selectedItems.length) {
                const selectedItems = this.state.selectedItems.map(item => {
                  return item.rowKey.value === currentItem.id ? selectedItem : item;
                });
                this.setState({selectedItems: [...selectedItems]});
              }
              this.setState({selectedItem});
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
      const request = new MetadataEntityDeleteFromProject(
        this.props.folderId,
        this.props.metadataClass
      );
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
    if (this.props.folder.pending && !this.props.folder.loaded) {
      return null;
    }
    const metadataManager = roleModel.writeAllowed(this.props.folder.value) &&
      this.props.folderId !== undefined &&
      roleModel.isManager.entities(this);
    if (
      metadataManager &&
      !this.props.hideUploadMetadataBtn && !this.props.readOnly
    ) {
      const Actions = {
        addMetadata: 'add-metadata',
        upload: 'upload',
        deleteClass: 'delete',
        showAttributes: 'show-attributes',
        transfer: 'transfer'
      };
      const triggerMenuItem = ({key}) => {
        switch (key) {
          case Actions.addMetadata:
            this.openAddInstanceForm();
            break;
          case Actions.upload:
            if (this.uploadButton) {
              this.uploadButton.triggerClick();
            }
            break;
          case Actions.deleteClass:
            this.deleteMetadataClassConfirm();
            break;
          case Actions.showAttributes:
            this.setState({metadata: !this.state.metadata});
            break;
          case Actions.transfer:
            this.onOpenUploadToBucketDialog();
            break;
        }
      };
      const menuItems = [];
      menuItems.push((
        <Menu.Item
          key={Actions.addMetadata}
          className={Actions.addMetadata}
        >
          <Icon
            type="plus"
            style={{marginRight: 5}}
          />
          Add instance
        </Menu.Item>
      ));
      menuItems.push((
        <Menu.Item
          key={Actions.upload}
          className={Actions.upload}
        >
          <Icon
            type="upload"
            style={{marginRight: 5}}
          />
          Upload metadata
        </Menu.Item>
      ));
      if (
        this.transferJobId &&
        this.transferJobVersion &&
        this.currentClassEntityPathFields.length > 0
      ) {
        menuItems.push((
          <Menu.Item
            key={Actions.transfer}
            className={Actions.transfer}
          >
            <Icon
              type="cloud-upload-o"
              style={{marginRight: 5}}
            />
            Transfer to the cloud
          </Menu.Item>
        ));
        menuItems.push((
          <Menu.Divider key="divider-1" />
        ));
      }
      menuItems.push((
        <Menu.Item
          key={Actions.deleteClass}
          className={Actions.deleteClass}
          style={{color: 'red'}}
        >
          <Icon
            type="delete"
            style={{marginRight: 5}}
          />
          Delete class
        </Menu.Item>
      ));
      menuItems.push((
        <Menu.Divider key="divider-2" />
      ));
      menuItems.push((
        <Menu.Item
          key={Actions.showAttributes}
          className={Actions.showAttributes}
        >
          {
            this.state.metadata ? 'Hide attributes' : 'Show attributes'
          }
        </Menu.Item>
      ));
      const menu = (
        <Menu
          onClick={triggerMenuItem}
        >
          {menuItems}
        </Menu>
      );
      return (
        <Dropdown
          overlay={menu}
          trigger={['click']}
        >
          <Button
            size="small"
            style={{lineHeight: 1, margin: '0 0 0 5px'}}
          >
            <Icon
              type="setting"
            />
          </Button>
        </Dropdown>
      );
    }
    return (
      <Button
        key="metadata"
        id={this.state.metadata ? 'hide-metadata-button' : 'show-metadata-button'}
        style={{
          lineHeight: 1,
          marginLeft: 5
        }}
        size="small"
        onClick={() => this.setState({metadata: !this.state.metadata})}>
        {
          this.state.metadata ? 'Hide attributes' : 'Show attributes'
        }
      </Button>
    );
  };

  get tableColumns () {
    const onHeaderClicked = (e, key) => {
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
          onClick={(e) => onHeaderClicked(e, key)}
          className={styles.metadataColumnHeader}>
          {icon}{getColumnTitle(key)}
          {this.renderFilterButton(key)}
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
      const {column, index} = props;
      const item = props.original;
      const className = getCellClassName(item, styles.metadataColumnCell);
      return (
        <div className={className} style={{overflow: 'hidden', textOverflow: 'ellipsis'}} >
          <div className="selector"
            style={{position: 'absolute', bottom: 0, right: 0, width: 10, height: 10, backgroundColor: 'transparent', zIndex: 100}} />
          {reactElementFn()}
        </div>
      );
    };

    return [
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
      ...this.columns.filter(c => this.state.selectedColumns.indexOf(c) >= 0).map((key, index) => {
        return {
          accessor: key,
          index,
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
          })};
      })];
  };

  renderTableActions = () => {
    const {
      filterModel = {},
      selectedItems = [],
      selectedItemsAreShowing
    } = this.state;
    const selectedItemsString =
      `${selectedItems.length} selected item${selectedItems.length === 1 ? '' : 's'}`;
    const renderClearFiltersButton = () => {
      const {
        filters = [],
        startDateFrom,
        endDateTo,
        searchQueries = []
      } = filterModel;
      const filtersEnabled = filters.length > 0 ||
        !!startDateFrom ||
        !!endDateTo ||
        searchQueries.length > 0;
      if (!selectedItemsAreShowing && filtersEnabled) {
        return (
          <Button
            key="clear_filters"
            size="small"
            onClick={this.onClearFilters}
            style={{marginLeft: 5, marginRight: 5}}
          >
            <Icon
              type="close"
            />
            Clear filters
          </Button>
        );
      }
      return null;
    };
    const renderSelectionInfo = () => {
      if (selectedItems.length === 0) {
        return null;
      }
      if (selectedItemsAreShowing && selectedItems.length > 0) {
        return (
          <span
            style={{marginLeft: 5}}
            key="info"
          >
            {/* eslint-disable-next-line */}
            Currently viewing {selectedItemsString}
          </span>
        );
      }
      return null;
    };
    const renderSelectionControl = () => {
      if (selectedItems.length === 0) {
        return null;
      }
      const Actions = {
        delete: 'delete-selected-items',
        clearSelection: 'clear-selection',
        copySelection: 'copy-selection'
      };
      const triggerMenuItem = ({key}) => {
        switch (key) {
          case Actions.delete:
            this.onDeleteSelectedItems();
            break;
          case Actions.clearSelection:
            this.onClearSelectionItems();
            break;
          case Actions.copySelection:
            this.onCopySelectionItems();
            break;
        }
      };
      const menuItems = [(
        <Menu.Item
          key={Actions.clearSelection}
        >
          Clear selection
        </Menu.Item>
      )];
      if (
        roleModel.writeAllowed(this.props.folder.value) &&
        !this.props.readOnly &&
        roleModel.isManager.entities(this)
      ) {
        menuItems.push((
          <Menu.Item
            key={Actions.copySelection}
          >
            Copy
          </Menu.Item>
        ));
        menuItems.push((<Menu.Divider key="divider" />));
        menuItems.push((
          <Menu.Item
            key={Actions.delete}
            style={{color: 'red'}}
          >
            Delete
          </Menu.Item>
        ));
      }
      const menu = (
        <Menu
          onClick={triggerMenuItem}
          style={{width: 150}}
        >
          {menuItems}
        </Menu>
      );
      return (
        <Button.Group>
          <Button
            size="small"
            onClick={this.handleClickShowSelectedItems}
          >
            {
              selectedItemsAreShowing
                ? 'Show all metadata items'
                : `Show ${selectedItemsString}`
            }
          </Button>
          <Dropdown
            overlay={menu}
            trigger={['click']}
          >
            <Button
              size="small"
            >
              <Icon type="down" />
            </Button>
          </Dropdown>
        </Button.Group>
      );
    };
    const renderRunButton = () => {
      if (
        this.state.currentProjectId &&
        roleModel.writeAllowed(this.props.folder.value) &&
        !this.props.readOnly &&
        roleModel.isManager.entities(this)
      ) {
        return (
          <Button
            key="run"
            size="small"
            type="primary"
            disabled={selectedItems.length === 0}
            onClick={() => this.setState({configurationBrowserVisible: true})}>
            RUN
          </Button>
        );
      }
      return null;
    };
    return (
      <Row
        className={styles.metadataAdditionalActions}
        type="flex"
        justify="space-between"
        align="middle"
      >
        <div
          style={{
            display: 'inline-flex',
            flexDirection: 'row',
            alignItems: 'center'
          }}
        >
          {renderSelectionControl()}
          {renderClearFiltersButton()}
          {renderSelectionInfo()}
        </div>
        {renderRunButton()}
      </Row>
    );
  };

  handleClickShowSelectedItems = () => {
    this.setState({
      selectedItem: null,
      metadata: false,
      selectedItemsAreShowing: !this.state.selectedItemsAreShowing
    }, () => this.paginationOnChange(FIRST_PAGE));
  }

  paginationOnChange = async (page) => {
    const {filterModel} = this.state;
    filterModel.page = page;
    this.setState(
      {filterModel},
      () => this.loadData()
    );
  }

  render () {
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row
          type="flex"
          justify="end"
          align="middle"
          style={{
            minHeight: 41,
            marginBottom: 6,
            flexWrap: 'wrap'
          }}
        >
          <div
            className={styles.itemHeader}
            style={{flex: 'initial', marginRight: 'auto'}}
          >
            <Breadcrumbs
              id={parseInt(this.props.folderId)}
              type={ItemTypes.metadata}
              textEditableField={this.props.metadataClass}
              readOnlyEditableField
              icon="appstore-o"
              iconClassName={styles.editableControl}
              subject={this.props.folder.value}
            />
          </div>
          <Input.Search
            style={{
              minWidth: 200,
              marginLeft: 5,
              flex: 1
            }}
            id="search-metadata-input"
            placeholder="Search"
            value={this.state.searchQuery}
            onPressEnter={this.onSearchQueriesChanged}
            onChange={(e) => {
              this.setState({searchQuery: e.target.value});
            }}
            size="small"
          />
          <DropdownWithMultiselect
            onColumnSelect={this.onColumnSelect}
            onSetOrder={this.onSetOrder}
            selectedColumns={this.state.selectedColumns}
            columns={this.columns}
            onResetColumns={this.onResetColumns}
            columnNameFn={getColumnTitle}
            size="small"
            style={{marginLeft: 5}}
          />
          {this.renderActions()}
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
            style={{display: 'none'}}
            title={'Upload metadata'}
            action={MetadataEntityUpload.uploadUrl(this.props.folderId)}
            onInitialized={component => {
              this.uploadButton = component;
            }}
          />
        </Row>
        {this.renderContent()}
        <AddInstanceForm
          folderId={this.props.folderId}
          visible={this.state.addInstanceFormVisible}
          pending={this.state.operationInProgress}
          onCreate={this.operationWrapper(this.addInstance)}
          onCancel={this.closeAddInstanceForm}
          entityType={this.currentMetadataClassId}
          entityTypes={this.entityTypes}
        />
        <UploadToDatastorageForm
          visible={this.state.uploadToBucketVisible}
          fields={
            this.currentClassEntityFields
              .filter(f => f.type.toLowerCase() !== 'path').map(f => f.name)
          }
          pathFields={this.currentClassEntityPathFields.map(f => f.name)}
          onTransfer={this.onStartUploadToBucket}
          onClose={this.onCloseUploadToBucketDialog}
        />
      </div>
    );
  };

  componentDidMount () {
    const {route, router} = this.props;
    if (route && router) {
      router.setRouteLeaveHook(route, this.leavePageWithSelectedItems.bind(this));
    };
    (async () => {
      await this.loadColumns(this.props.folderId, this.props.metadataClass);
      this.setState({selectedColumns: [...this.columns]});
      await this.loadData();
      await this.loadCurrentProject();
    })();
  };

  leavePageWithSelectedItems (nextLocation) {
    const {router} = this.props;
    const {selectedItemsCanBeSkipped} = this.state;

    const resetSelectedItemsCanBeSkipped = () => {
      this.resetSelectedItemsTimeout = setTimeout(
        () => this.setState && this.setState({selectedItemsCanBeSkipped: false}),
        0
      );
    };

    const leave = nextLocation => {
      this.setState({selectedItemsCanBeSkipped: true},
        () => {
          router.push(nextLocation);
          resetSelectedItemsCanBeSkipped();
        }
      );
      return true;
    };

    if (this.state.selectedItems && this.state.selectedItems.length && !selectedItemsCanBeSkipped) {
      Modal.confirm({
        title: 'All selected items will be reset. Continue?',
        onOk () {
          leave(nextLocation);
        },
        onCancel: () => this.props.onReloadTree(false),
        okText: 'Yes',
        cancelText: 'No'
      });
      return false;
    }
  };

  componentWillUnmount () {
    this.resetSelectedItemsTimeout && clearTimeout(this.resetSelectedItemsTimeout);
  }

  async componentWillReceiveProps (nextProps) {
    if (nextProps.initialSelection) {
      this.setState({selectedItems: nextProps.initialSelection});
    }
    if (nextProps.folderId !== this.props.folderId ||
      nextProps.metadataClass !== this.props.metadataClass) {
      this.setState({
        searchQuery: undefined,
        selectedItem: null,
        selectedItems: [],
        selectedItemsAreShowing: false,
        filterModel: {
          filters: [],
          folderId: parseInt(nextProps.folderId),
          metadataClass: nextProps.metadataClass,
          orderBy: [],
          page: 1,
          pageSize: PAGE_SIZE,
          searchQueries: []
        }
      });
      if (nextProps.onSelectItems) {
        nextProps.onSelectItems(this.state.selectedItems);
      }
      this._totalCount = 0;
      await this.props.entityFields.fetch();
      await this.loadColumns(nextProps.folderId, nextProps.metadataClass);
      this.setState({selectedColumns: [...this.columns]});
      await this.loadData();
    }
    if (nextProps.folderId !== this.props.folderId) {
      await this.loadCurrentProject();
    }
  };
}
