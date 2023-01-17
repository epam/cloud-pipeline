/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
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
  Icon,
  Input,
  message,
  Modal,
  Pagination,
  Row
} from 'antd';
import Menu, {MenuItem, Divider} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import {
  ContentMetadataPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY
} from '../../special/splitPanel';
import styles from './Browser.css';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import MetadataPanel from '../../special/metadataPanel/MetadataPanel';
import DropdownWithMultiselect from '../../special/DropdownWithMultiselect';
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
import DeleteFromProject from '../../../models/folderMetadata/MetadataEntityDeleteFromProject';
import MetadataEntityDeleteList from '../../../models/folderMetadata/MetadataEntityDeleteList';
import ConfigurationBrowser from '../launch/dialogs/configuration-browser';
import ConfigurationRun from '../../../models/configuration/ConfigurationRun';
import PipelineRunner from '../../../models/pipelines/PipelineRunner';
import {ItemTypes} from '../model/treeStructureFunctions';
import Breadcrumbs from '../../special/Breadcrumbs';
import {MetadataSampleSheetValue} from '../../special/sample-sheet';
import displayDate from '../../../utils/displayDate';
import HiddenObjects from '../../../utils/hidden-objects';
import RangeDatePicker from './metadata-controls/RangeDatePicker';
import FilterControl from './metadata-controls/FilterControl';
import parseSearchQuery from './metadata-controls/parse-search-query';
import getDefaultMetadataProperties, {
  METADATA_KEYS
} from './metadata-controls/get-default-metadata-properties';
import PredefinedFilterButton from './metadata-controls/predefined-filter-button';
import getPathParameters from './metadata-controls/get-path-parameters';
import * as autoFillEntities from './metadata-controls/auto-fill-entities';
import ngsProject, {ngsProjectMachineRuns, ngsProjectSamples} from '../../../utils/ngs-project';
import * as metadataFilterUtilities from './metadata-controls/metadata-filters';
import NGSMetadataUpdateSampleSheet from '../../../models/metadata/NGSMetadataUpdateSampleSheet';
import NGSMetadataDeleteSampleSheet from '../../../models/metadata/NGSMetadataDeleteSampleSheet';
import RunsAttribute, {isRunsValue} from '../../special/metadata/special/runs-attribute';
import AttributeValue from '../../special/metadata/special/attribute-value';
import {
  getConditionStyles,
  getPredefinedFilterForItem,
  parseScheme
} from './metadata-controls/predefined-filter-utilities';

const AutoFillEntitiesMarker = autoFillEntities.AutoFillEntitiesMarker;
const AutoFillEntitiesActions = autoFillEntities.AutoFillEntitiesActions;

const FIRST_PAGE = 1;
const PAGE_SIZE = 20;
const ASCEND = 'ascend';
const DESCEND = 'descend';
const FILTER_OPERATORS = {
  greater: 'GE',
  less: 'LE'
};

function filterColumns (column) {
  return !column.predefined || ['externalId', 'createdDate'].includes(column.name);
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

function isPredefined (name) {
  return ['externalId', 'createdDate'].includes(name);
}

function getDefaultColumnName (displayName) {
  if (/^created date$/i.test(displayName)) {
    return 'createdDate';
  }
  return unmapColumnName(displayName);
}

function getDefaultSorting (sorting = [], columns = []) {
  return sorting.map(rule => {
    const [field, order = 'ASC'] = rule.trim().split(':');
    return {
      field,
      desc: /^desc$/i.test(order)
    };
  }).filter(rule => columns.find(col => mapColumnName(col) === rule.field));
}

function getColumnTitle (key) {
  if (key === 'createdDate') {
    return 'Created Date';
  }
  return key;
}

function makeIndexOf (array) {
  return column => {
    const index = (array || []).findIndex(o => o.key === column.key);
    if (index === -1) {
      return Infinity;
    }
    return index;
  };
}

function makeCurrentOrderSort (array) {
  const indexOf = makeIndexOf(array);
  return (a, b) => indexOf(a) - indexOf(b);
}

@connect({
  folders,
  pipelinesLibrary
})
@roleModel.authenticationInfo
@inject('preferences', 'dataStorages')
@HiddenObjects.checkMetadataFolders(p => (p.params || p).id)
@HiddenObjects.checkMetadataClassesWithParent(p => (p.params || p).id, p => (p.params || p).class)
@inject((
  {
    folders,
    pipelinesLibrary,
    authenticatedUserInfo,
    preferences,
    dataStorages,
    routing
  },
  params
) => {
  let componentParameters = params;
  let filters = [];
  if (params.params) {
    // Router renderer
    componentParameters = params.params;
    filters = routing && routing.location
      ? metadataFilterUtilities.parse(routing.location.query)
      : [];
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
    dataStorages,
    pipelinesLibrary,
    filters
  };
})
@ngsProject
@ngsProjectMachineRuns
@ngsProjectSamples
@observer
export default class Metadata extends React.Component {
  static propTypes = {
    onSelectItems: PropTypes.func,
    initialSelection: PropTypes.array,
    hideUploadMetadataBtn: PropTypes.bool,
    readOnly: PropTypes.bool
  };

  @observable keys;

  metadataRequest = {};
  externalMetadataEntity = {};

  state = {
    loading: false,
    metadata: false,
    searchQuery: undefined,
    selectedItem: null,
    selectedItems: this.props.initialSelection ? this.props.initialSelection : [],
    selectedItemsCanBeSkipped: false,
    selectedItemsAreShowing: false,
    cellsActions: undefined,
    cellsSelection: undefined,
    hoveredCell: undefined,
    defaultColumnsNames: [],
    columns: [],
    totalCount: 0,
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
    filterComponentVisible: false,
    addInstanceFormVisible: false,
    operationInProgress: false,
    configurationBrowserVisible: false,
    uploadToBucketVisible: false,
    copyEntitiesDialogVisible: false,
    currentMetadata: [],
    currentMetadataConditions: [],
    predefinedFilters: [],
    predefinedConditions: [],
    defaultOrderBy: [],
    defaultMetadataPropertiesFetched: false,
    defaultMetadataPropertiesFetching: false
  };

  uploadButton;

  @computed
  get entityTypes () {
    const {entityFields, metadataClasses} = this.props;
    if (entityFields.loaded && metadataClasses.loaded) {
      const mappedEntityFields = (entityFields.value || [])
        .map(e => e);
      const ignoreClasses = new Set(mappedEntityFields.map(f => f.metadataClass.id));
      const otherClasses = (metadataClasses.value || [])
        .filter(({id}) => !ignoreClasses.has(id))
        .map(metadataClass => ({
          fields: [],
          metadataClass: {...metadataClass, outOfProject: true}
        }));
      return [
        ...mappedEntityFields,
        ...otherClasses
      ];
    }
    return [];
  }

  @computed
  get transferJobId () {
    const {preferences} = this.props;
    if (preferences.loaded) {
      return +preferences.getPreferenceValue('storage.transfer.pipeline.id');
    }
    return null;
  }

  @computed
  get transferJobVersion () {
    const {preferences} = this.props;
    if (preferences.loaded) {
      return preferences.getPreferenceValue('storage.transfer.pipeline.version');
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

  get predefinedFilters () {
    const {predefinedFilters = []} = this.state;
    const {metadataClass} = this.props;
    return predefinedFilters
      .filter(predefined => [metadataClass, '*'].includes(predefined.metadataClass));
  }

  get predefinedConditions () {
    const {predefinedConditions = []} = this.state;
    const {metadataClass} = this.props;
    return predefinedConditions
      .filter(predefined => [metadataClass, '*'].includes(predefined.metadataClass));
  }

  getColumnType = (key) => {
    if (key === 'createdDate') {
      return 'Date';
    }
    const entityField = this.currentClassEntityFields
      .find(field => field.name === key);
    if (entityField) {
      return entityField.type;
    }
    return 'string';
  };

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

  onDateRangeChanged = (range, key) => {
    const filterModel = {...this.state.filterModel};
    const {
      from,
      to,
      emptyValue
    } = range || {};
    if (key === 'createdDate') {
      filterModel.startDateFrom = from;
      filterModel.endDateTo = to;
      return this.setState(
        {filterModel},
        () => {
          this.clearSelection();
          this.loadData();
        }
      );
    }
    const filtered = this.state.filterModel.filters
      .filter(filter => filter.key !== key);
    let periodFilters;
    if (emptyValue) {
      periodFilters = [{
        key,
        values: []
      }];
    } else if (from || to) {
      const wrapFilter = (aDate, operator) => {
        if (aDate) {
          return {
            key,
            operator,
            values: [aDate]
          };
        }
        return undefined;
      };
      periodFilters = [
        wrapFilter(from, FILTER_OPERATORS.greater),
        wrapFilter(to, FILTER_OPERATORS.less)
      ].filter(Boolean);
    } else {
      periodFilters = [];
    }
    filterModel.filters = [...filtered, ...periodFilters];
    return this.setState(
      {filterModel},
      () => {
        this.clearSelection();
        this.loadData();
      }
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
        this.clearSelection();
        await this.props.entityFields.fetch();
        await this.loadColumns({append: true});
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

  handleFilterApplied = (key, fieldFilter) => {
    const filterModel = {...this.state.filterModel};
    if (key && !!fieldFilter) {
      const filterObj = {key: unmapColumnName(key), values: fieldFilter};
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
      () => {
        this.clearSelection();
        this.loadData();
      }
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
        .map(o => ({
          ...o,
          field: unmapColumnName(o.field),
          predefined: isPredefined(unmapColumnName(o.field))
        }));
      filters = (filterModel.filters || [])
        .map(o => ({
          key: unmapColumnName(o.key),
          values: o.values || [],
          predefined: isPredefined(unmapColumnName(o.key)),
          ...(o.operator && {operator: o.operator})
        }));
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
        const {value} = this.metadataRequest;
        if (value) {
          this.setState({totalCount: value.totalCount});
          if (value.elements && value.elements.length) {
            this._classEntity = {
              id: value.elements[0].classEntity.id,
              name: value.elements[0].classEntity.name
            };
          }
          currentMetadata = (value.elements || []).map(v => {
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
      this.setState({totalCount: selectedItems.length});

      const firstRow = Math.max((page - 1) * pageSize, 0);
      const lastRow = Math.min(page * pageSize, selectedItems.length);

      if (orderBy && orderBy.length) {
        const sortedSelectedItems = this.sortSelectedItems();

        currentMetadata = sortedSelectedItems.slice(firstRow, lastRow);
      } else {
        currentMetadata = this.state.selectedItems.slice(firstRow, lastRow);
      }
    }
    const conditions = currentMetadata
      .map(item => getPredefinedFilterForItem(item, this.predefinedConditions));
    this.setState({
      loading: false,
      currentMetadata,
      currentMetadataConditions: conditions
    });
  };

  sortSelectedItems = () => {
    let selectedItems = [...this.state.selectedItems];
    const orderBy = [...this.state.filterModel.orderBy];

    selectedItems.sort((a, b) => {
      function valuesAreEqual (item1, item2) {
        if (!item1 && !item2) {
          return true;
        }
        return item1 === item2;
      }

      for (let i in orderBy) {
        const [field, desc] = Object.values(orderBy[i]);
        const item1 = a[field] ? a[field].value : null;
        const item2 = b[field] ? b[field].value : null;
        if (!valuesAreEqual(item1, item2)) {
          if (item1 < item2 || item2 === null) {
            return desc ? 1 : -1;
          }
          if (item1 > item2 || item1 === null) {
            return desc ? -1 : 1;
          }
        }
      }
      return 0;
    });

    return selectedItems;
  }

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
    if (this.state.selectedItemsAreShowing || this.isSampleSheetColumn(key)) {
      return null;
    }
    const handleControlVisibility = (visible) => {
      this.setState({
        filterComponentVisible: visible
      });
    };
    const {filterModel = {}} = this.state;
    const {
      filters = [],
      startDateFrom,
      endDateTo
    } = filterModel;
    const filter = filters.find(filter => filter.key === unmapColumnName(key));
    const values = filter
      ? filter.values
      : undefined;
    const button = (
      <Button
        shape="circle"
        onClick={(e) => e.stopPropagation()}
        className={classNames(
          this.filterApplied(key)
            ? 'cp-primary'
            : 'cp-text-not-important',
          'cp-transparent-background'
        )}
        style={{
          marginLeft: 5,
          border: 'none'
        }}
      >
        <Icon
          type="filter"
          className={
            classNames(
              {'cp-primary': !!values || (key === 'createdDate' && (startDateFrom || endDateTo))}
            )
          }
        />
      </Button>
    );
    if (/^date$/i.test(this.getColumnType(key))) {
      let dateInfo = {};
      if (key === 'createdDate') {
        dateInfo.from = startDateFrom;
        dateInfo.to = endDateTo;
      } else {
        const currentFilters = filters
          .filter(filter => filter.key === unmapColumnName(key));
        dateInfo.emptyValue = currentFilters.length === 1 &&
          currentFilters[0].values &&
          currentFilters[0].values.length === 0;
        const filterFrom = currentFilters
          .find(filter => filter.operator === FILTER_OPERATORS.greater);
        const filterTo = currentFilters
          .find(filter => filter.operator === FILTER_OPERATORS.less);
        dateInfo.from = filterFrom ? filterFrom.values[0] : undefined;
        dateInfo.to = filterTo ? filterTo.values[0] : undefined;
      }
      return (
        <RangeDatePicker
          from={dateInfo.from}
          to={dateInfo.to}
          onChange={(e) => this.onDateRangeChanged(e, key)}
          visibilityChanged={handleControlVisibility}
          supportEmptyValue={key !== 'createdDate'}
          emptyValue={dateInfo.emptyValue}
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
        visibilityChanged={handleControlVisibility}
      >
        {button}
      </FilterControl>
    );
  }

  onDeleteSelectedItems = () => {
    const removeConfiguration = async () => {
      const selectedIds = this.state.selectedItems.map(item => item.rowKey.value);
      const request = new MetadataEntityDeleteList();
      await request.send(selectedIds);
      if (request.error) {
        message.error(request.error, 5);
      }
      this.setState({selectedItems: [], selectedItem: null, metadata: false}, () => {
        this.clearSelection();
      });
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

  resetColumns = (columns) => {
    return new Promise((resolve) => {
      const {
        defaultColumnsNames
      } = this.state;
      if ((defaultColumnsNames || []).length > 0) {
        const defaultColumns = defaultColumnsNames.map(name => ({
          key: mapColumnName({name})
        }));
        const newColumns = columns
          .sort(makeCurrentOrderSort(defaultColumns))
          .map(column => ({
            ...column,
            selected: defaultColumns.findIndex(c => c.key === column.key) >= 0
          }));
        resolve(newColumns);
      } else {
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
          if ((a.name || '').toLowerCase() > (b.name || '').toLowerCase()) {
            return 1;
          }
          if ((a.name || '').toLowerCase() < (b.name || '').toLowerCase()) {
            return -1;
          }
          return 0;
        };
        const predefinedSort = (a, b) => b.predefined - a.predefined;
        const newColumns = columns
          .sort(externalIdSort)
          .sort(predefinedSort)
          .map(column => ({
            ...column,
            selected: true
          }));
        resolve(newColumns);
      }
    });
  };

  loadColumns = (options) => {
    const {
      reset = false,
      append = false
    } = options || {};
    const {folderId, metadataClass} = this.props;
    const metadataEntityKeysRequest =
      new MetadataEntityKeys(folderId, metadataClass);
    return new Promise((resolve) => {
      const {
        columns: currentColumns = []
      } = this.state;
      const columnIsSelected = columnKey => currentColumns.length === 0 ||
        !!currentColumns.find(c => c.selected && c.key === columnKey);
      this.setState({loading: true}, () => {
        metadataEntityKeysRequest
          .fetch()
          .then(() => {
            if (metadataEntityKeysRequest.error) {
              throw new Error(metadataEntityKeysRequest.error);
            }
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
            const columns = (metadataEntityKeysRequest.value || [])
              .sort(externalIdSort)
              .sort(predefinedSort)
              .filter(filterColumns)
              .map(column => ({
                ...column,
                key: mapColumnName(column),
                selected: columnIsSelected(mapColumnName(column))
              }));
            return Promise.resolve(columns);
          })
          .then(columns => {
            if (reset) {
              return this.resetColumns(columns);
            }
            return Promise.resolve(columns);
          })
          .then(columns => {
            if (Array.isArray(columns) && append) {
              columns
                .sort(makeCurrentOrderSort(currentColumns))
                .forEach(column => {
                  column.selected = column.selected ||
                    currentColumns.findIndex(o => o.key === column.key) === -1;
                });
            }
            return Promise.resolve(columns);
          })
          .then(columns => {
            if (!columns.some(column => column.selected)) {
              columns.forEach(column => {
                column.selected = true;
              });
            }
            return Promise.resolve(columns);
          })
          .catch((e) => {
            message.error(e.message, 5);
            return Promise.resolve([]);
          })
          .then((columns) => this.setState({
            loading: false,
            columns
          }, () => resolve()));
      });
    });
  };

  onEditSampleSheet = async (machineRun, sampleSheet) => {
    if (machineRun && machineRun.rowKey && machineRun.rowKey.value) {
      const hide = message.loading('Updating sample sheet...', 0);
      try {
        const machineRunId = machineRun.rowKey.value;
        const {folderId} = this.props;
        const request = new NGSMetadataUpdateSampleSheet();
        await request
          .send({
            folderId: Number(folderId),
            machineRunId: Number(machineRunId),
            content: sampleSheet ? btoa(sampleSheet) : btoa('')
          });
        if (request.error) {
          throw new Error(request.error);
        }
        await this.loadData();
      } catch (e) {
        message.error(e.message, 5);
      } finally {
        hide();
      }
    }
  };

  onRemoveSampleSheet = async (machineRun) => {
    if (machineRun && machineRun.rowKey && machineRun.rowKey.value) {
      const hide = message.loading('Removing sample sheet and associated samples...', 0);
      try {
        const machineRunId = machineRun.rowKey.value;
        const {folderId} = this.props;
        const request = new NGSMetadataDeleteSampleSheet(folderId, machineRunId);
        await request.send({});
        if (request.error) {
          throw new Error(request.error);
        }
        await this.loadData();
      } catch (e) {
        message.error(e.message, 5);
      } finally {
        hide();
      }
    }
  };

  onArrayReferencesClick = (event, key, data, referenceType, item) => {
    const {
      ngsProjectInfo,
      ngsProjectMachineRuns,
      ngsProjectSamples
    } = this.props;
    event.stopPropagation();
    const defaultAction = () => {
      const selectedItem = {};
      selectedItem[key] = {type: data.type, value: data.value};
      this.setState({metadata: true, selectedItem: selectedItem});
    };
    if (
      ngsProjectInfo.isNGSProject &&
      ngsProjectMachineRuns.isMachineRunsMetadataClass &&
      item &&
      item.ID &&
      item.ID.value
    ) {
      ngsProjectInfo
        .fetchPreferences()
        .then(() => {
          if (
            ngsProjectInfo.isSampleClassName(referenceType)
          ) {
            return ngsProjectSamples.getMachineRunField(referenceType);
          }
          return Promise.reject(new Error('Not a NGS project'));
        })
        .then(machineRunFieldInfo => {
          if (machineRunFieldInfo) {
            const {
              className,
              fieldName
            } = machineRunFieldInfo;
            const {
              router,
              folderId
            } = this.props;
            const query = metadataFilterUtilities
              .build([{key: fieldName, values: [item.ID.value]}]);
            if (query) {
              router.push(`/folder/${folderId}/metadata/${className}?${query}`);
            } else {
              router.push(`/folder/${folderId}/metadata/${className}`);
            }
            return Promise.resolve();
          }
          return Promise.reject(new Error('Machine run field is missing'));
        })
        .catch(() => defaultAction());
    } else {
      defaultAction();
    }
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
    }, () => {
      this.clearSelection();
      this.loadData();
    });
  };

  onOrderByChanged = async (key, value) => {
    if (key) {
      const filterModel = {...this.state.filterModel};
      const [currentOrderBy] = filterModel.orderBy.filter(f => f.field === key);
      if (currentOrderBy) {
        const index = filterModel.orderBy.indexOf(currentOrderBy);
        const desc = currentOrderBy.desc;
        if (desc) {
          const updatedcurrentOrderBy = {field: key, desc: !desc};
          filterModel.orderBy.splice(index, 1, updatedcurrentOrderBy);
        } else {
          filterModel.orderBy.splice(index, 1);
        }
      }
      if (!currentOrderBy && value) {
        filterModel.orderBy.push({field: key, desc: value === DESCEND});
      }
      this.setState({filterModel}, () => {
        this.clearSelection();
        this.loadData();
      });
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
    const currentColumns = [...this.state.columns];
    const column = currentColumns.find(obj => obj.key === item);
    if (column) {
      column.selected = !column.selected;
      this.setState(
        {columns: [...currentColumns]},
        this.resetColumnsFiltersAndSorting
      );
    }
  };

  resetColumnsFiltersAndSorting = () => {
    const {
      columns,
      filterModel: oldFilters = {}
    } = this.state;
    const {
      orderBy,
      filters,
      startDateFrom,
      endDateTo,
      ...filterModelRest
    } = oldFilters;
    const selectedColumnsKeys = new Set(
      columns
        .filter(column => column.selected)
        .map(column => column.key)
    );
    const filterModel = {
      orderBy: orderBy.filter(col => selectedColumnsKeys.has(col.field)),
      filters: filters.filter(filter => selectedColumnsKeys.has(filter.key)),
      startDateFrom: selectedColumnsKeys.has('createdDate') ? startDateFrom : undefined,
      endDateTo: selectedColumnsKeys.has('createdDate') ? endDateTo : undefined,
      ...filterModelRest
    };
    this.setState({
      filterModel
    }, () => {
      this.clearSelection();
      this.loadData();
    });
  };

  onResetColumns = () => {
    this.resetColumns(this.state.columns)
      .then(columns => this.setState({columns}, this.resetColumnsFiltersAndSorting));
  };

  onSetOrder = (order) => {
    this.setState({columns: order}, this.clearSelection);
  };

  onRowClick = (item) => {
    const [selectedItem] =
      this.state.currentMetadata
        .filter(column => column.rowKey === item.rowKey);
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
        filterModel: {...filterModel},
        searchQuery: undefined
      },
      () => {
        this.clearSelection();
        this.loadData();
      }
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

  onSelectConfigurationConfirm = async (selectedConfiguration, expression) => {
    this.onCloseConfigurationBrowser();
    if (!selectedConfiguration) {
      return;
    }
    const {
      id,
      entries = []
    } = selectedConfiguration || {};
    const hide = message.loading('Launching...', 0);
    const parameters = await getPathParameters(this.props.pipelinesLibrary, this.props.folderId);
    const mapParameters = (entry) => ({
      ...entry,
      configuration: {
        ...(entry.configuration || {}),
        parameters: {
          ...parameters,
          ...((entry.configuration || {}).parameters || {})
        }
      }
    });
    const request = new ConfigurationRun(expression);
    await request.send({
      id,
      entries: entries.map(mapParameters),
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
      this.setState({
        selectedItemsCanBeSkipped: true
      }, () => {
        SessionStorageWrapper.navigateToActiveRuns(this.props.router);
      });
    }
  };

  onCloseConfigurationBrowser = () => {
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

  cellIsSpreading = (row, column) => {
    const spreadSelection = this.getSpreadSelection();
    if (spreadSelection) {
      const {start, end} = spreadSelection;
      return (
        start.column <= column &&
        start.row <= row &&
        end.column >= column &&
        end.row >= row
      );
    }
  };
  getCurrentSelection = () => {
    const {cellsSelection} = this.state;
    if (cellsSelection) {
      const {
        start,
        end,
        ...rest
      } = cellsSelection;
      return {
        start: {
          column: Math.min(start.column, end.column),
          row: Math.min(start.row, end.row)
        },
        end: {
          column: Math.max(start.column, end.column),
          row: Math.max(start.row, end.row)
        },
        ...rest
      };
    }
    return undefined;
  }
  getSpreadSelection = () => {
    const selection = this.getCurrentSelection();
    if (selection && selection.spread) {
      const {
        column: spreadColumn,
        row: spreadRow
      } = selection.spread;
      const start = {
        column: spreadColumn > selection.start.column ? selection.start.column : spreadColumn,
        row: spreadRow > selection.start.row ? selection.start.row : spreadRow
      };
      const end = {
        column: spreadColumn >= selection.start.column ? spreadColumn : selection.end.column,
        row: spreadRow >= selection.start.row ? spreadRow : selection.end.row
      };
      return {
        start,
        end
      };
    }
    return undefined;
  }
  handleStartSelection = (opts) => {
    if (this.props.readOnly || this.state.filterComponentVisible) {
      return;
    }
    const {e, rowInfo, column: columnInfo} = opts;
    const selectable = columnInfo.selectable === undefined || columnInfo.selectable;
    if (!selectable) {
      // cell is not selectable, ignore it
      return;
    }
    if (columnInfo.index === undefined) {
      // selection cell, ignore it
      return;
    }
    e.stopPropagation();
    const selection = this.getCurrentSelection();
    const spreadSelection = this.getSpreadSelection();
    const row = rowInfo.index;
    const column = columnInfo.index;
    const isSpreading = autoFillEntities.isAutoFillEntitiesMarker(e.target);
    if (!isSpreading) {
      this.setState({
        cellsSelection: {
          start: {column, row},
          end: {column, row},
          selecting: true,
          spreading: false
        },
        cellsActions: []
      });
    } else if (
      !selection ||
      (
        !autoFillEntities.cellIsSelected(spreadSelection, column, row) &&
        !autoFillEntities.cellIsSelected(selection, column, row)
      )
    ) {
      // spreading && no current selection
      this.setState({
        cellsSelection: {
          start: {column, row},
          end: {column, row},
          spread: {
            column,
            row,
            start: {
              column,
              row
            }
          },
          selecting: false,
          spreading: true
        },
        cellsActions: []
      });
    } else {
      // spreading current selection
      this.setState({
        cellsSelection: {
          start: {...selection.start},
          end: {...selection.end},
          spread: {
            column,
            row,
            start: {
              column,
              row
            }
          },
          selecting: false,
          spreading: true
        },
        cellsActions: []
      });
    }
  }

  applySelectionAction = (action, revert = true) => {
    const {loadingMessage, action: fn, revert: revertFn} = action;
    const hide = message.loading(loadingMessage || 'Processing...', 0);
    const revertToInitialValues = revert && revertFn
      ? revertFn
      : () => Promise.resolve();
    revertToInitialValues()
      .then(fn)
      .then((result) => {
        const {currentMetadata} = this.state;
        result.forEach(item => {
          const index = (currentMetadata || [])
            .findIndex(o => o.rowKey && item.rowKey && o.rowKey.value === item.rowKey.value);
          if (index >= 0) {
            currentMetadata.splice(index, 1, item);
          }
        });
        this.setState({
          currentMetadata,
          currentMetadataConditions: currentMetadata
            .map(item => getPredefinedFilterForItem(item, this.predefinedConditions))
        });
        return Promise.resolve();
      })
      .catch(e => message.error(e.message, 5))
      .then(() => hide());
  };

  handleFinishSelection = () => {
    const {cellsSelection} = this.state;
    if (!cellsSelection) {
      return;
    }
    const spreadSelection = this.getSpreadSelection();
    const selection = this.getCurrentSelection();
    if (
      cellsSelection.spreading &&
      cellsSelection.spread &&
      cellsSelection.spread.apply &&
      spreadSelection
    ) {
      const dataItemFrom = Math.min(
        spreadSelection.start.row,
        selection.start.row
      );
      const dataItemTo = Math.max(
        spreadSelection.end.row,
        selection.end.row
      );
      const {
        currentMetadata,
        columns: tableColumns
      } = this.state;
      const backup = (currentMetadata || [])
        .slice(dataItemFrom, dataItemTo + 1);
      const elements = backup.map((item, index) => ({
        item,
        row: index + dataItemFrom
      }));
      const columns = tableColumns
        .filter(column => column.selected && !this.isSampleSheetColumn(column.key))
        .map((column, index) => ({key: mapColumnName(column), column: index}));
      const actions = autoFillEntities.buildAutoFillActions(
        elements,
        columns,
        selection,
        spreadSelection,
        backup,
        {
          classId: this.currentMetadataClassId,
          className: this.props.metadataClass,
          parentId: this.props.folderId
        }
      );
      if (actions && actions.length > 0) {
        this.applySelectionAction(
          actions[0],
          false
        );
      }
      this.setState({
        cellsActions: actions,
        cellsSelection: {
          start: {...spreadSelection.start},
          end: {...spreadSelection.end},
          selecting: false,
          spreading: false
        }
      });
    } else if (cellsSelection.selecting) {
      this.setState({
        cellsSelection: {
          ...cellsSelection,
          selecting: false,
          spreading: false
        }
      });
    }
  }
  isHoveredCell = (row, column) => {
    const {hoveredCell} = this.state;
    return hoveredCell && hoveredCell.row === row && hoveredCell.column === column;
  }
  handleCellSelection = (opts) => {
    if (this.props.readOnly || this.state.filterComponentVisible) {
      return;
    }
    const {e, rowInfo, column: columnInfo} = opts;
    e.stopPropagation();
    const {cellsSelection: selection, hoveredCell} = this.state;
    const row = rowInfo.index;
    const column = columnInfo.index;
    const selectable = columnInfo.selectable === undefined || columnInfo.selectable;
    if (!selection) {
      if (!selectable) {
        this.setState({hoveredCell: undefined});
      } else if (
        column !== undefined && (
          !hoveredCell ||
          hoveredCell.column !== column ||
          hoveredCell.row !== row
        )
      ) {
        this.setState({
          hoveredCell: {column, row}
        });
      }
      return;
    }
    if (!selection.selecting && !selection.spreading) {
      const cellSelected = autoFillEntities.cellIsSelected(selection, column, row);
      if (!selectable) {
        this.setState({
          hoveredCell: undefined
        });
      } else if (cellSelected && !!hoveredCell) {
        this.setState({
          hoveredCell: undefined
        });
      } else if (
        !cellSelected &&
        (
          !hoveredCell ||
          hoveredCell.column !== column ||
          hoveredCell.row !== row
        )
      ) {
        this.setState({
          hoveredCell: {column, row}
        });
      }
      return;
    }
    if (
      selection.selecting &&
      (
        selection.end.row !== row || selection.end.column !== column
      )
    ) {
      this.setState({
        cellsSelection: {
          ...selection,
          end: {row, column}
        },
        hoveredCell: undefined
      });
      return;
    }
    if (!selection.selecting && selection.spreading) {
      const {spread} = selection;
      if (spread.column === column && spread.row === row) {
        return;
      }
      const dy = Math.abs(row - spread.start.row);
      const dx = Math.abs(column - spread.start.column);
      if (dy > dx) {
        this.setState({
          cellsSelection: {
            ...selection,
            spread: {
              row,
              column: spread.start.column,
              start: spread.start,
              apply: true
            }
          },
          hoveredCell: undefined
        });
      } else {
        this.setState({
          cellsSelection: {
            ...selection,
            spread: {
              column,
              row: spread.start.row,
              start: spread.start,
              apply: true
            }
          },
          hoveredCell: undefined
        });
      }
    }
  }
  resetSelection = (e) => {
    if (e.key === 'Escape') {
      this.setState({
        cellsSelection: undefined,
        selecting: false,
        cellsActions: undefined
      });
    }
  }
  clearHovering = () => {
    this.setState({hoveredCell: undefined});
  }
  clearSelection = () => {
    this.setState({
      cellsSelection: undefined,
      cellsActions: undefined
    });
  };

  renderContent = () => {
    const getCellStyle = (column, row) => {
      const selectionArea = this.getCurrentSelection();
      const spreadingArea = this.getSpreadSelection();
      const selected = autoFillEntities.cellIsSelected(selectionArea, column, row);
      const spreadSelected = autoFillEntities.cellIsSelected(spreadingArea, column, row);
      const hovered = !selected && !spreadSelected && this.isHoveredCell(row, column);

      const top = hovered ||
        autoFillEntities.isTopSideCell(selectionArea, column, row) ||
        autoFillEntities.isTopSideCell(spreadingArea, column, row);
      const right = hovered ||
        autoFillEntities.isRightSideCell(selectionArea, column, row) ||
        autoFillEntities.isRightSideCell(spreadingArea, column, row);
      const bottom = hovered ||
        autoFillEntities.isBottomSideCell(selectionArea, column, row) ||
        autoFillEntities.isBottomSideCell(spreadingArea, column, row);
      const left = hovered ||
        autoFillEntities.isLeftSideCell(selectionArea, column, row) ||
        autoFillEntities.isLeftSideCell(spreadingArea, column, row);
      return classNames({
        [styles.cell]: true,
        'cp-library-metadata-spread-top-cell': top,
        'cp-library-metadata-spread-bottom-cell': bottom,
        'cp-library-metadata-spread-right-cell': right,
        'cp-library-metadata-spread-left-cell': left,
        'cp-library-metadata-spread-selected': spreadSelected,
        'cp-library-metadata-spread-cell-hovered': hovered,
        'cp-library-metadata-spread-cell-selected': selected
      });
    };

    const renderTable = () => {
      return [
        <ReactTable
          key="table"
          className={`${styles.metadataTable} cp-library-metadata-table -striped -highlight`}
          sortable={false}
          minRows={0}
          columns={this.tableColumns}
          data={this.state.currentMetadata}
          getTableProps={() => ({
            style: {
              overflowY: 'hidden',
              userSelect: 'none',
              borderCollapse: 'collapse',
              borderRadius: 5
            },
            onMouseOut: this.clearHovering
          })}
          getTrGroupProps={() => ({style: {borderBottom: 'none'}})}
          getTdProps={(state, rowInfo, column, instance) => ({
            onMouseDown: (e) => this.handleStartSelection({e, rowInfo, column}),
            onMouseMove: (e) => this.handleCellSelection({e, rowInfo, column}),
            onKeyPress: (e) => this.resetSelection(e),
            onClick: (e) => {
              if (autoFillEntities.isAutoFillEntitiesMarker(e.target)) {
                return;
              }
              if (e) {
                e.stopPropagation();
              }
              if (column.id === 'selection') {
                this.onItemSelect(rowInfo.row._original);
              } else if (column.selectable === undefined || column.selectable) {
                this.onRowClick(rowInfo.row._original);
              }
              this.clearSelection();
            },
            className: getCellStyle(column.index, rowInfo.index)
          })}
          getResizerProps={() => ({style: {width: '6px', right: '-3px'}})}
          PadRowComponent={
            () =>
              <div className={styles.metadataColumnCell}>
                <span>{'\u00A0'}</span>
              </div>
          }
          showPagination={false}
          NoDataComponent={
            () => (
              <div
                className={
                  classNames(
                    styles.noData,
                    'cp-library-metadata-panel-placeholder'
                  )
                }
              >
                No rows found
              </div>
            )
          }
        />,
        <Row key="pagination" type="flex" justify="end" style={{marginTop: 10}}>
          <Pagination
            size="small"
            pageSize={PAGE_SIZE}
            current={this.state.filterModel.page}
            total={this.state.totalCount}
            onChange={async (page) => this.paginationOnChange(page)} />
        </Row>
      ];
    };

    const renderConfigurationBrowser = () => {
      return this.props.folderId ? (
        <ConfigurationBrowser
          onCancel={this.onCloseConfigurationBrowser}
          onSelect={this.onSelectConfigurationConfirm}
          visible={this.state.configurationBrowserVisible}
          folderId={Number(this.props.folderId)}
          metadataClassName={this.props.metadataClass}
        />
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
        className={'cp-transparent-background'}
        style={{flex: 1, overflow: 'auto'}}
        onPanelClose={onPanelClose}>
        <div key={CONTENT_PANEL_KEY}>
          {this.renderTableActions()}
          {this.renderPredefinedFilters()}
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
              await this.loadColumns({append: true});
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
              this.setState({selectedItem}, () => this.clearSelection());
              await this.props.folder.fetch();
              if (this.props.onReloadTree) {
                this.props.onReloadTree(true);
              }
            }}
            pathAttributes={this.currentClassEntityPathFields.map(o => o.name)}
          />
        }
      </ContentMetadataPanel>
    );
  };

  deleteMetadataClassConfirm = () => {
    const onDeleteMetadataClass = async () => {
      const hide = message.loading(`Removing class '${this.props.metadataClass}'...`, -1);
      const request = new DeleteFromProject(
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
        this.props.router.push(`/folder/${this.props.folderId}/metadata`);
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
        <MenuItem
          key={Actions.addMetadata}
          className={classNames(styles.menuItem, Actions.addMetadata)}
        >
          <Icon
            type="plus"
            style={{marginRight: 5}}
          />
          Add instance
        </MenuItem>
      ));
      menuItems.push((
        <MenuItem
          key={Actions.upload}
          className={classNames(styles.menuItem, Actions.upload)}
        >
          <Icon
            type="upload"
            style={{marginRight: 5}}
          />
          Upload metadata
        </MenuItem>
      ));
      if (
        this.transferJobId &&
        this.transferJobVersion &&
        this.currentClassEntityPathFields.length > 0
      ) {
        menuItems.push((
          <MenuItem
            key={Actions.transfer}
            className={classNames(styles.menuItem, Actions.transfer)}
          >
            <Icon
              type="cloud-upload-o"
              style={{marginRight: 5}}
            />
            Transfer to the cloud
          </MenuItem>
        ));
        menuItems.push((
          <Divider key="divider-1" />
        ));
      }
      menuItems.push((
        <MenuItem
          key={Actions.deleteClass}
          className={classNames(styles.menuItem, Actions.deleteClass, 'cp-danger')}
        >
          <Icon
            type="delete"
            style={{marginRight: 5}}
          />
          Delete class
        </MenuItem>
      ));
      menuItems.push((
        <Divider key="divider-2" />
      ));
      menuItems.push((
        <MenuItem
          key={Actions.showAttributes}
          className={classNames(styles.menuItem, Actions.showAttributes)}
        >
          {
            this.state.metadata ? 'Hide attributes' : 'Show attributes'
          }
        </MenuItem>
      ));
      const menu = (
        <Menu
          onClick={triggerMenuItem}
          selectedKeys={[]}
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
            id="metadata-actions-button"
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

  isSampleSheetColumn = (key) => {
    const {
      ngsProjectInfo,
      ngsProjectMachineRuns
    } = this.props;
    return ngsProjectInfo.isNGSProject &&
      ngsProjectMachineRuns.isMachineRunsMetadataClass &&
      ngsProjectMachineRuns.isSampleSheetValue(key);
  };

  get tableColumns () {
    const {currentMetadataConditions = []} = this.state;
    const onHeaderClicked = (e, key) => {
      if (e) {
        e.stopPropagation();
      }
      if (this.isSampleSheetColumn(key)) {
        return;
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
      let icon, orderNumber;
      if (orderBy && !this.isSampleSheetColumn(key)) {
        let iconStyle = {fontSize: 10, marginRight: 5};
        if (this.state.filterModel.orderBy.length > 1) {
          const number = this.state.filterModel.orderBy.indexOf(orderBy) + 1;
          iconStyle = {fontSize: 10, marginRight: 0};
          orderNumber = <sup style={{marginRight: 5}}>{number}</sup>;
        }
        if (orderBy.desc) {
          icon = <Icon style={iconStyle} type="caret-down" />;
        } else {
          icon = <Icon style={iconStyle} type="caret-up" />;
        }
      }
      return (
        <span
          onClick={(e) => onHeaderClicked(e, key)}
          className={styles.metadataColumnHeader}>
          {icon}{orderNumber}{getColumnTitle(key)}
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
    const selection = this.getCurrentSelection();
    const spreadSelection = this.getSpreadSelection();
    const isSpreadCell = (column, row) => (
      !autoFillEntities.cellIsSelected(selection, column, row) &&
      !autoFillEntities.cellIsSelected(spreadSelection, column, row) &&
      this.isHoveredCell(row, column)
    ) || (
      selection &&
      !selection.spreading &&
      autoFillEntities.isRightCornerCell(selection, column, row)
    );
    const isActionsCell = (column, row) => this.state.cellsActions &&
      this.state.cellsActions.length > 0 &&
      selection &&
      autoFillEntities.isRightCornerCell(selection, column, row);
    const cellWrapper = (props, reactElementFn) => {
      const {column, index} = props;
      const item = props.original;
      const cellClassName = getCellClassName(item, styles.metadataColumnCell);
      const selected = cellClassName.search('selected') > -1;
      const condition = currentMetadataConditions[index];
      const {
        style,
        className: filterClassName
      } = getConditionStyles(condition, selected);
      const className = classNames(
        cellClassName,
        filterClassName,
        {
          'cp-library-metadata-table-cell-selected': selected
        }
      );
      return (
        <div
          className={className}
          style={{
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            ...style
          }}
        >
          <AutoFillEntitiesMarker
            visible={isSpreadCell(column.index, index)}
          />
          {
            isActionsCell(column.index, index) && (
              <AutoFillEntitiesActions
                actions={this.state.cellsActions}
                callback={this.applySelectionAction}
              />
            )
          }
          {reactElementFn() || '\u00A0'}
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
          padding: 0
        },
        className: classNames(styles.metadataCheckboxCell, 'cp-library-metadata-table-cell'),
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
      ...this.state.columns.filter(c => c.selected).map(({key}, index) => {
        return {
          accessor: key,
          index,
          style: {
            cursor: this.props.readOnly || this.isSampleSheetColumn(key)
              ? 'default'
              : 'cell',
            padding: 0
          },
          ...(
            this.isSampleSheetColumn(key)
              ? {width: 280, resizable: false, selectable: false}
              : {}
          ),
          className: 'cp-library-metadata-table-cell',
          Header: () => renderTitle(key),
          Cell: props => cellWrapper(props, () => {
            const data = props.value;
            const item = props.original;
            const condition = currentMetadataConditions[props.index];
            const icon = index === 0 && condition && condition.scheme && condition.scheme.icon
              ? (<Icon type={condition.scheme.icon} style={{marginRight: 5}} />)
              : undefined;
            if (this.isSampleSheetColumn(key)) {
              return (
                <MetadataSampleSheetValue
                  value={data ? data.value : undefined}
                  onChange={content => this.onEditSampleSheet(item, content)}
                  onRemove={() => this.onRemoveSampleSheet(item)}
                >
                  {icon}
                </MetadataSampleSheetValue>
              );
            }
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
                } catch (___) { }
                let value = `${count} ${referenceType}(s)`;
                return (
                  <a
                    title={value}
                    className={styles.actionLink}
                    onClick={(e) => this.onArrayReferencesClick(
                      e,
                      key,
                      data,
                      referenceType,
                      item
                    )}
                  >
                    {icon}{value}
                  </a>
                );
              }
              if (data.type.toLowerCase().endsWith(':id')) {
                return <a
                  title={data.value}
                  className={styles.actionLink}
                  onClick={(e) => this.onReferenceTypesClick(e, data)}>
                  {icon}{data.value}
                </a>;
              }
              if (data.type.toLowerCase() === 'path') {
                return (
                  <AttributeValue
                    value={data.value}
                    showFileNameOnly
                  >
                    {icon}
                  </AttributeValue>
                );
              }
              if (/^date$/i.test(data.type)) {
                return (
                  <span title={data.value}>
                    {icon}
                    {displayDate(data.value)}
                  </span>
                );
              }
              if (isRunsValue(data.value)) {
                return (
                  <RunsAttribute
                    value={data.value}
                  />
                );
              }
              return (
                <span title={data.value}>
                  {icon}
                  {data.value}
                </span>
              );
            }
          })
        };
      })];
  };

  renderTableActions = () => {
    const {
      filterModel = {},
      selectedItems = [],
      selectedItemsAreShowing,
      totalCount,
      loading
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
            style={{margin: '0 5px'}}
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
            style={{margin: '0 5px'}}
            key="info"
          > Currently viewing {selectedItemsString}
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
        <MenuItem
          key={Actions.clearSelection}
          className={classNames(styles.menuItem, Actions.clearSelection)}
        >
          Clear selection
        </MenuItem>
      )];
      if (
        roleModel.writeAllowed(this.props.folder.value) &&
        !this.props.readOnly &&
        roleModel.isManager.entities(this)
      ) {
        menuItems.push((
          <MenuItem
            key={Actions.copySelection}
            className={classNames(styles.menuItem, Actions.copySelection)}
          >
            Copy
          </MenuItem>
        ));
        menuItems.push((<Divider key="divider" />));
        menuItems.push((
          <MenuItem
            key={Actions.delete}
            className={classNames(styles.menuItem, Actions.delete, 'cp-danger')}
          >
            Delete
          </MenuItem>
        ));
      }
      const menu = (
        <Menu
          onClick={triggerMenuItem}
          style={{width: 150}}
          selectedKeys={[]}
        >
          {menuItems}
        </Menu>
      );
      return (
        <Button.Group style={{margin: '0 5px'}}>
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
        this.props.folderId &&
        roleModel.writeAllowed(this.props.folder.value) &&
        !this.props.readOnly
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
    const totalRecordsInfo = `${totalCount || 'No'} record${totalCount !== 1 ? 's' : ''} found`;
    return (
      <Row
        className={classNames(
          styles.metadataAdditionalActions,
          'cp-library-metadata-additional-actions'
        )}
        type="flex"
        justify="space-between"
        align="center"
      >
        <div
          style={{
            display: 'inline-flex',
            flexDirection: 'row',
            alignItems: 'center',
            flexWrap: 'wrap'
          }}
        >
          {
            <span
              style={{marginRight: 5}}
            >
              {totalRecordsInfo}
              {
                loading && (<Icon type="loading" />)
              }
            </span>
          }
          {renderSelectionControl()}
          {renderClearFiltersButton()}
          {renderSelectionInfo()}
        </div>
        {renderRunButton()}
      </Row>
    );
  };

  renderPredefinedFilters = () => {
    const {selectedItemsAreShowing} = this.state;
    if (!selectedItemsAreShowing && this.predefinedFilters.length > 0) {
      return (
        <div
          className={
            classNames(
              styles.metadataPredefinedFilters,
              'cp-library-metadata-additional-actions'
            )
          }
        >
          {
            this.predefinedFilters.map((filter, index) => {
              return (
                <PredefinedFilterButton
                  className={styles.metadataPredefinedFilter}
                  key={`predefined_filter_${index}_${filter.name || filter.title || ''}`}
                  filter={filter}
                  onClick={this.handleClickPredefinedFilter}
                  currentFilter={this.state.filterModel}
                  folderId={this.props.folderId}
                  metadataClass={this.props.metadataClass}
                />
              );
            })
          }
        </div>
      );
    }
    return null;
  };

  handleClickShowSelectedItems = () => {
    this.setState({
      selectedItem: null,
      metadata: false,
      selectedItemsAreShowing: !this.state.selectedItemsAreShowing
    }, () => this.paginationOnChange(FIRST_PAGE));
  }

  handleClickPredefinedFilter = (filter) => {
    if (!filter) {
      this.onClearFilters();
      return;
    }
    const {filterModel} = this.state;
    this.setState(
      {
        filterModel: {
          ...(filterModel || {}),
          ...(filter || {}),
          searchQueries: [],
          page: 1
        },
        searchQuery: undefined
      },
      () => {
        this.clearSelection();
        this.loadData();
      }
    );
  }

  paginationOnChange = async (page) => {
    const {filterModel} = this.state;
    filterModel.page = page;
    this.setState(
      {
        filterModel: {...filterModel}
      },
      () => {
        this.clearSelection();
        this.loadData();
      }
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
            columns={this.state.columns}
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

  getParents = async () => {
    const {folderId, pipelinesLibrary} = this.props;
    const parents = [];
    let pNumber = 0;

    function checkFolder (folder) {
      if (folder.id === Number(folderId)) {
        pNumber = 1;
        parents.push({
          key: `p${pNumber}`,
          value: folder.name
        });
        return true;
      } else {
        if (folder.childFolders) {
          for (let subfolder of folder.childFolders) {
            const targetFolder = checkFolder(subfolder);
            if (targetFolder) {
              if (folder.name) {
                pNumber += 1;
                parents.push({
                  key: `p${pNumber}`,
                  value: folder.name
                });
              }
              return targetFolder;
            }
          }
        }
        return false;
      }
    }

    await pipelinesLibrary.fetch();
    const pipelinesLibraryValue = pipelinesLibrary.value;
    if (pipelinesLibraryValue && pipelinesLibraryValue.childFolders) {
      checkFolder(pipelinesLibraryValue);
    }
    return parents;
  };

  componentDidMount () {
    const {
      authenticatedUserInfo,
      route,
      router
    } = this.props;
    if (route && router) {
      router.setRouteLeaveHook(route, this.leavePageWithSelectedItems.bind(this));
    }
    this.onFolderChanged();
    authenticatedUserInfo
      .fetchIfNeededOrWait()
      .then(() => this.fetchDefaultMetadataProperties());
    document.addEventListener('keydown', this.resetSelection);
    window.addEventListener('mouseup', this.handleFinishSelection);
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    const metadataClassChanged = prevProps.metadataClass !== this.props.metadataClass;
    const folderChanged = prevProps.folderId !== this.props.folderId;
    const filtersChanged = metadataFilterUtilities
      .filtersChanged(prevProps.filters, this.props.filters);
    if (
      metadataClassChanged ||
      folderChanged ||
      filtersChanged
    ) {
      this.onFolderChanged(folderChanged);
    } else {
      (this.fetchDefaultMetadataProperties)();
    }
    if (prevProps.initialSelection !== this.props.initialSelection) {
      this.updateInitialSelection();
    }
  }

  updateInitialSelection = () => {
    this.setState({selectedItems: (this.props.initialSelection || []).slice()});
  };

  onFolderChanged = (folderChanged = true) => {
    if (this.props.onSelectItems) {
      this.props.onSelectItems([]);
    }
    const newState = {
      currentMetadata: [],
      currentMetadataConditions: [],
      columns: [],
      searchQuery: undefined,
      selectedItem: null,
      selectedItems: [],
      selectedItemsAreShowing: false,
      filterModel: {
        filters: (this.props.filters || []).slice(),
        folderId: parseInt(this.props.folderId),
        metadataClass: this.props.metadataClass,
        orderBy: [],
        page: 1,
        pageSize: PAGE_SIZE,
        searchQueries: []
      },
      totalCount: 0
    };
    if (folderChanged) {
      newState.defaultMetadataPropertiesFetched = false;
      newState.defaultMetadataPropertiesFetching = true;
      newState.defaultColumnsNames = [];
    }
    this.setState(newState, () => {
      this.clearSelection();
      const promises = [
        folderChanged
          ? this.fetchDefaultMetadataProperties(folderChanged)
          : this.loadColumns({reset: true}),
        folderChanged ? this.props.entityFields.fetch() : Promise.resolve()
      ];
      return Promise.all(promises).then(() => {
        const {defaultOrderBy, columns} = this.state;
        this.setState(prevState => ({
          filterModel: {
            ...prevState.filterModel,
            orderBy: getDefaultSorting(
              defaultOrderBy,
              columns
            )
          }
        }), async () => {
          await this.loadData();
        });
      });
    });
  };

  fetchDefaultMetadataProperties = (folderChanged = false) => {
    const {
      defaultMetadataPropertiesFetched,
      defaultMetadataPropertiesFetching
    } = this.state;
    const {authenticatedUserInfo, folderId} = this.props;
    return new Promise((resolve) => {
      if (
        authenticatedUserInfo.loaded &&
        ((!defaultMetadataPropertiesFetched &&
        !defaultMetadataPropertiesFetching) || folderChanged)
      ) {
        this.setState({
          defaultMetadataPropertiesFetching: true
        }, () => {
          getDefaultMetadataProperties(folderId, authenticatedUserInfo.value)
            .then(metadata => {
              const {columns, filters, columnsSorting} = METADATA_KEYS;
              const defaultColumnsNames = (metadata[columns] || []).map(getDefaultColumnName);
              const predefined = Object.entries(metadata[filters] || {})
                .map(([key, filters]) => {
                  const classes = key.split(/\s*,\s*/g);
                  return classes
                    .map(metadataClass => filters.map(filter => ({...filter, metadataClass})))
                    .reduce((r, c) => ([...r, ...c]), []);
                })
                .reduce((r, c) => ([...r, ...c]), [])
                .map(fc => ({
                  ...fc,
                  scheme: parseScheme(fc.scheme),
                  isCondition: /(^|\+)(condition)($|\+)/i.test(fc.type),
                  isFilter: !fc.type || /(^|\+)(filter)($|\+)/i.test(fc.type)
                }));
              this.setState({
                defaultColumnsNames,
                defaultMetadataPropertiesFetching: false,
                defaultMetadataPropertiesFetched: true,
                predefinedFilters: predefined
                  .filter(fc => fc.isFilter),
                predefinedConditions: predefined
                  .filter(fc => fc.isCondition),
                defaultOrderBy: metadata[columnsSorting]
              }, () => {
                this.loadColumns({reset: true})
                  .then(() => {
                    const {columns: metadataColumns} = this.state;
                    this.setState(prevState => ({
                      filterModel: {
                        ...prevState.filterModel,
                        orderBy: getDefaultSorting(
                          metadata[columnsSorting],
                          metadataColumns
                        )
                      }
                    }), () => resolve());
                  });
              });
            });
        });
      } else {
        resolve();
      }
    });
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
    document.removeEventListener('keydown', this.resetSelection);
    window.removeEventListener('mouseup', this.handleFinishSelection);
  }
}
