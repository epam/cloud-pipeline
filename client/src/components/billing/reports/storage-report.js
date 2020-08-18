/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
  Summary
} from './charts';
import {costTickFormatter, DisplayUser, numberFormatter, ResizableContainer} from './utilities';
import Filters, {RUNNER_SEPARATOR, REGION_SEPARATOR} from './filters';
import {Period, getPeriod} from './periods';
import StorageFilter, {StorageFilters} from './filters/storage-filter';
import Export, {ExportComposers} from './export';
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
import styles from './reports.css';

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
  if (/^file$/i.test(type)) {
    storages = new GetGroupedFileStoragesWithPrevious(filters, true);
    storagesTable = new GetGroupedFileStorages(filters, true);
    filterBy = GetBillingData.FILTER_BY.fileStorages;
  } else if (/^object$/i.test(type)) {
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
    storagesTable
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
      key: 'cost',
      title: 'Cost',
      dataIndex: 'value',
      render: (value) => value ? costTickFormatter(value) : null
    },
    {
      key: 'volume',
      title: 'Volume',
      dataIndex: 'usage',
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
      render: (value) => moment.utc(value).format('DD MMM YYYY')
    }
  ];
  const dataSource = Object.values(
    discounts.applyGroupedDataDiscounts(storages.value || {}, discountsFn)
  );
  const paginationEnabled = storages && storages.loaded
    ? storages.totalPages > 1
    : false;
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

const RenderTable = observer(renderTable);

class StorageReports extends React.Component {
  state = {
    dataSampleKey: StorageFilters.value.key
  };

  onChangeDataSample = (key) => {
    this.setState({
      dataSampleKey: key
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

  render () {
    const {storages, storagesTable, summary} = this.props;
    const composers = [
      {
        composer: ExportComposers.summaryComposer,
        options: [summary]
      },
      {
        composer: ExportComposers.defaultComposer,
        options: [
          storages,
          {
            owner: 'owner',
            cloud_provider: 'provider',
            cloud_region: 'region',
            created_date: 'created'
          }
        ]
      }
    ];
    const costsUsageSelectorHeight = 30;
    return (
      <Discounts.Consumer>
        {
          (o, storageDiscounts) => (
            <Export.Consumer
              className={styles.chartsContainer}
              composers={composers}
            >
              <Layout
                layout={StorageReportLayout.Layout}
                gridStyles={StorageReportLayout.GridStyles}
              >
                <div key={StorageReportLayout.Panels.summary}>
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
                            quota={false}
                            title={this.getSummaryTitle()}
                            style={{width, height}}
                          />
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
                <div key={StorageReportLayout.Panels.storages}>
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
                            <BarChart
                              request={storages}
                              discounts={storageDiscounts}
                              title={this.getTitle()}
                              top={tablePageSize}
                              style={{height: height / 2.0 - costsUsageSelectorHeight}}
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
                            <RenderTable
                              storages={storagesTable}
                              discounts={storageDiscounts}
                              height={height / 2.0 - costsUsageSelectorHeight}
                            />
                          </div>
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

export default inject(injection)(
  Filters.attach(
    observer(StorageReports)
  )
);
