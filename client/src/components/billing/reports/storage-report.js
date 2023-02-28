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
import {
  Pagination,
  Table
} from 'antd';
import moment from 'moment-timezone';
import {
  BarChart,
  BillingTable,
  Summary,
  StorageLayers
} from './charts';
import {
  costTickFormatter,
  numberFormatter,
  DisplayUser,
  ResizableContainer
} from './utilities';
import BillingNavigation, {RUNNER_SEPARATOR, REGION_SEPARATOR} from '../navigation';
import {Period, getPeriod} from '../../special/periods';
import StorageFilter, {StorageFilters} from './filters/storage-filter';
import Export from './export';
import Discounts, {discounts} from './discounts';
import {
  GetBillingData,
  GetGroupedStorages,
  GetGroupedStoragesWithPrevious,
  GetGroupedFileStorages,
  GetGroupedFileStoragesWithPrevious,
  GetGroupedObjectStorages,
  GetGroupedObjectStoragesWithPrevious
} from '../../../models/billing';
import {StorageReportLayout, Layout} from './layout';

const tablePageSize = 10;

function injection (stores, props) {
  const {location, params} = props;
  const {type} = params || {};
  const {
    user: userQ,
    group: groupQ,
    period = Period.month,
    range,
    region: regionQ
  } = location.query;
  const periodInfo = getPeriod(period, range);
  const group = groupQ ? groupQ.split(RUNNER_SEPARATOR) : undefined;
  const user = userQ ? userQ.split(RUNNER_SEPARATOR) : undefined;
  const cloudRegionId = regionQ && regionQ.length ? regionQ.split(REGION_SEPARATOR) : undefined;
  const filters = {
    group,
    user,
    type,
    cloudRegionId,
    ...periodInfo
  };
  let filterBy = GetBillingData.FILTER_BY.storages;
  let storages;
  let storagesTable;
  let storageType;
  let loadCostDetails = false;
  if (/^file$/i.test(type)) {
    storageType = 'FILE_STORAGE';
    storages = new GetGroupedFileStoragesWithPrevious(filters, true);
    storagesTable = new GetGroupedFileStorages(filters, true);
    filterBy = GetBillingData.FILTER_BY.fileStorages;
  } else if (/^object$/i.test(type)) {
    // todo: pass loadCostDetails to requests
    loadCostDetails = true;
    storageType = 'OBJECT_STORAGE';
    storages = new GetGroupedObjectStoragesWithPrevious(filters, true);
    storagesTable = new GetGroupedObjectStorages(filters, true);
    filterBy = GetBillingData.FILTER_BY.objectStorages;
  } else {
    storages = new GetGroupedStoragesWithPrevious(filters, true);
    storagesTable = new GetGroupedStorages(filters, true);
  }
  storages.fetch();
  storagesTable.fetch();
  const summary = new GetBillingData({
    ...filters,
    filterBy
  });
  summary.fetch();

  return {
    user,
    group,
    type,
    summary,
    storages,
    storagesTable,
    storageType
  };
}

function renderTable ({storages, discounts: discountsFn, height}) {
  if (!storages || !storages.loaded) {
    return null;
  }
  const columns = [
    {
      key: 'storage',
      title: 'Storage',
      render: ({info, name}) => {
        return info && info.name ? info.pathMask || info.name : name;
      }
    },
    {
      key: 'owner',
      title: 'Owner',
      dataIndex: 'owner',
      render: owner => (<DisplayUser userName={owner} />)
    },
    {
      key: 'billingCenter',
      title: 'Billing Center',
      dataIndex: 'billingCenter'
    },
    {
      key: 'storageType',
      title: 'Type',
      dataIndex: 'storageType'
    },
    {
      key: 'cost',
      title: 'Cost',
      dataIndex: 'value',
      render: (value) => value ? costTickFormatter(value) : null
    },
    {
      key: 'volume',
      title: 'Avg. Vol. (GB)',
      dataIndex: 'usage',
      render: (value) => value ? numberFormatter(value) : null
    },
    {
      key: 'volume current',
      title: 'Cur. Vol. (GB)',
      dataIndex: 'usageLast',
      render: (value) => value ? numberFormatter(value) : null
    },
    {
      key: 'region',
      title: 'Region',
      dataIndex: 'region'
    },
    {
      key: 'provider',
      title: 'Provider',
      dataIndex: 'provider'
    },
    {
      key: 'created',
      title: 'Created date',
      dataIndex: 'created',
      render: (value) => value ? moment.utc(value).format('DD MMM YYYY') : value
    }
  ];
  const dataSource = Object.values(
    discounts.applyGroupedDataDiscounts(storages.value || {}, discountsFn)
  );
  const paginationEnabled = storages && storages.loaded
    ? storages.totalPages > 1
    : false;
  const getRowClassName = (storage = {}) => {
    if (`${(storage.groupingInfo || {}).is_deleted}` === 'true') {
      return 'cp-warning-row';
    }
    return '';
  };
  return (
    <div>
      <div
        style={{
          position: 'relative',
          overflow: 'auto',
          maxHeight: height - (paginationEnabled ? 30 : 0),
          padding: 5
        }}
      >
        <Table
          className="cp-report-table"
          rowClassName={getRowClassName}
          rowKey={({info, name}) => {
            return info && info.id ? `storage_${info.id}` : `storage_${name}`;
          }}
          loading={storages.pending}
          dataSource={dataSource}
          columns={columns}
          pagination={false}
          size="small"
        />
      </div>
      {
        paginationEnabled && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              height: 30
            }}
          >
            <Pagination
              current={storages.pageNum + 1}
              pageSize={storages.pageSize}
              total={storages.totalPages * storages.pageSize}
              onChange={async (page) => {
                await storages.fetchPage(page - 1);
              }}
              size="small"
            />
          </div>
        )
      }
    </div>
  );
}

const getRandom = (min, max) => {
  return Math.floor(Math.random() * (max - min + 1) + min);
};

const detailsMock = {
  'Deep Archive': {
    storageClass: 'Deep Archive',
    cost: getRandom(3000, 9000),
    size: getRandom(500, 5000),
    oldVersionCost: getRandom(3000, 4000),
    oldVersionSize: getRandom(500, 5000)
  },
  'Glacier IR': {
    storageClass: 'Glacier IR',
    cost: getRandom(3000, 9000),
    size: getRandom(500, 5000),
    oldVersionCost: getRandom(3000, 4000),
    oldVersionSize: getRandom(500, 5000)
  },
  'Glacier': {
    storageClass: 'Glacier',
    cost: getRandom(3000, 9000),
    size: getRandom(500, 5000),
    oldVersionCost: getRandom(3000, 4000),
    oldVersionSize: getRandom(500, 5000)
  },
  'Standard': {
    storageClass: 'Standard',
    cost: getRandom(3000, 9000),
    size: getRandom(500, 5000),
    oldVersionCost: getRandom(3000, 4000),
    oldVersionSize: getRandom(500, 5000)
  }
};

const RenderTable = observer(renderTable);

class StorageReports extends React.Component {
  state = {
    dataSampleKey: StorageFilters.value.key,
    selectedStorageLayer: undefined
  };

  get layout () {
    const {type} = this.props;
    if (/^object$/i.test(type)) {
      return {
        ...StorageReportLayout,
        Layout: StorageReportLayout.ObjectsLayout
      };
    }
    return StorageReportLayout;
  }

  onChangeDataSample = (key) => {
    this.setState({
      dataSampleKey: key
    });
  };

  onSelectLayer = ({key}) => {
    const {selectedStorageLayer} = this.state;
    this.setState({selectedStorageLayer: selectedStorageLayer === key
      ? undefined
      : key
    });
  };

  getSummaryTitle = () => {
    const {type} = this.props;
    if (/^file$/i.test(type)) {
      return 'File storages usage';
    }
    if (/^object$/i.test(type)) {
      return 'Object storages usage';
    }
    return 'Storages usage';
  };

  getTitle = () => {
    const {type} = this.props;
    if (/^file$/i.test(type)) {
      return 'File storages';
    }
    if (/^object$/i.test(type)) {
      return 'Object storages';
    }
    return 'Storages';
  };

  get layersMock () {
    const labels = Object.values(detailsMock).map(detail => detail.storageClass);
    const getData = (key, labels) => {
      const data = [];
      labels.forEach((label, index) => {
        const current = detailsMock[label] || {};
        data[index] = current[key] || 0;
      });
      return data;
    };
    const filter = this.state.dataSampleKey === 'value'
      ? ['cost', 'oldVersionCost']
      : ['size', 'oldVersionSize'];
    const datasets = filter
      .map(key => {
        return {
          label: key,
          data: getData(key, labels)
        };
      });
    return {
      labels,
      datasets
    };
  }

  render () {
    const {
      storages,
      storagesTable,
      summary,
      user,
      group,
      type,
      filters = {},
      storageType,
      reportThemes
    } = this.props;
    const {period, range, region: cloudRegionId} = filters;
    const costsUsageSelectorHeight = 30;
    return (
      <Discounts.Consumer>
        {
          (o, storageDiscounts) => (
            <Export.Consumer
              exportConfiguration={{
                types: ['STORAGE'],
                user,
                group,
                period,
                range,
                filters: {
                  storage_type: storageType ? [storageType.toUpperCase()] : undefined,
                  cloudRegionId: cloudRegionId &&
                  cloudRegionId.length > 0
                    ? cloudRegionId
                    : undefined
                }
              }}
            >
              <Layout
                layout={this.layout.Layout}
                gridStyles={this.layout.GridStyles}
              >
                <div key={this.layout.Panels.summary}>
                  <Layout.Panel
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      minHeight: 0
                    }}
                  >
                    <BillingTable
                      storages={summary}
                      storagesDiscounts={storageDiscounts}
                      showQuota={false}
                    />
                    <ResizableContainer style={{flex: 1}}>
                      {
                        ({width, height}) => (
                          <Summary
                            storages={summary}
                            storagesDiscounts={storageDiscounts}
                            quota
                            title={this.getSummaryTitle()}
                            style={{width, height}}
                          />
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
                {/^object$/i.test(type) ? (
                  <div key={this.layout.Panels.storageLayers}>
                    <Layout.Panel>
                      <ResizableContainer style={{width: '100%', height: '100%'}}>
                        {
                          ({height}) => (
                            <div>
                              <div
                                style={{
                                  display: 'flex',
                                  flexDirection: 'row',
                                  justifyContent: 'center',
                                  alignItems: 'center',
                                  height: costsUsageSelectorHeight
                                }}
                              >
                                <StorageFilter
                                  onChange={this.onChangeDataSample}
                                  value={this.state.dataSampleKey}
                                />
                              </div>
                              <StorageLayers
                                highlightedLabel={this.layersMock.labels
                                  .indexOf(this.state.selectedStorageLayer)
                                }
                                onSelect={this.onSelectLayer}
                                data={this.layersMock}
                                title={'Object storage layers'}
                                style={{height: height - costsUsageSelectorHeight}}
                                dataSample={
                                  StorageFilters[this.state.dataSampleKey].dataSample
                                }
                                previousDataSample={
                                  StorageFilters[this.state.dataSampleKey].previousDataSample
                                }
                                valueFormatter={
                                  this.state.dataSampleKey === StorageFilters.value.key
                                    ? costTickFormatter
                                    : numberFormatter
                                }
                              />
                            </div>
                          )
                        }
                      </ResizableContainer>
                    </Layout.Panel>
                  </div>
                ) : (
                  null
                )}
                <div key={this.layout.Panels.storages}>
                  <Layout.Panel>
                    <ResizableContainer style={{width: '100%', height: '100%'}}>
                      {
                        ({height}) => (
                          <div>
                            {/* <div
                              style={{
                                display: 'flex',
                                flexDirection: 'row',
                                justifyContent: 'center',
                                alignItems: 'center',
                                height: costsUsageSelectorHeight
                              }}
                            >
                              <StorageFilter
                                onChange={this.onChangeDataSample}
                                value={this.state.dataSampleKey}
                              />
                            </div> */}
                            <BarChart
                              request={storages}
                              discounts={storageDiscounts}
                              title={this.getTitle()}
                              top={tablePageSize}
                              style={{height: height - costsUsageSelectorHeight}}
                              dataSample={
                                StorageFilters[this.state.dataSampleKey].dataSample
                              }
                              previousDataSample={
                                StorageFilters[this.state.dataSampleKey].previousDataSample
                              }
                              valueFormatter={
                                this.state.dataSampleKey === StorageFilters.value.key
                                  ? costTickFormatter
                                  : numberFormatter
                              }
                              highlightTickFn={
                                (storage) => `${(storage.groupingInfo || {}).is_deleted}` === 'true'
                              }
                              highlightTickStyle={{
                                fontColor: reportThemes.errorColor
                              }}
                            />
                          </div>
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
                <div key={this.layout.Panels.storagesTable}>
                  <Layout.Panel>
                    <ResizableContainer style={{width: '100%', height: '100%'}}>
                      {
                        ({height}) => (
                          <RenderTable
                            storages={storagesTable}
                            discounts={storageDiscounts}
                            height={height}
                          />
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
              </Layout>
            </Export.Consumer>
          )
        }
      </Discounts.Consumer>
    );
  }
}

export default inject('reportThemes')(
  inject(injection)(
    BillingNavigation.attach(
      observer(StorageReports)
    )
  )
);
