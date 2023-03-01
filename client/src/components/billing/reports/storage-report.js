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
import {computed} from 'mobx';
import classNames from 'classnames';
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
import {
  getBillingGroupingSortField,
  parseStorageMetrics,
  StorageMetrics
} from '../navigation/metrics';
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
  GetGroupedObjectStoragesWithPrevious,
  GetObjectStorageLayersInfo,
  preFetchBillingRequest
} from '../../../models/billing';
import {StorageReportLayout, Layout} from './layout';
import {
  getAggregateByStorageClass,
  getBillingGroupingOrderAggregate,
  getStorageClassName,
  getStorageClassNameByAggregate,
  parseStorageAggregate,
  StorageAggregate,
  DEFAULT_STORAGE_CLASS_ORDER, getStorageClassByAggregate
} from '../navigation/aggregate';
import styles from './storage-report.css';

const tablePageSize = 10;

function injection (stores, props) {
  const {location, params} = props;
  const {type} = params || {};
  const {
    user: userQ,
    group: groupQ,
    period = Period.month,
    range,
    region: regionQ,
    metrics: metricsQ,
    layer: aggregateQ
  } = location.query;
  const metrics = parseStorageMetrics(metricsQ);
  const aggregate = /^object$/i.test(type)
    ? parseStorageAggregate(aggregateQ)
    : undefined;
  const periodInfo = getPeriod(period, range);
  const group = groupQ ? groupQ.split(RUNNER_SEPARATOR) : undefined;
  const user = userQ ? userQ.split(RUNNER_SEPARATOR) : undefined;
  const cloudRegionId = regionQ && regionQ.length ? regionQ.split(REGION_SEPARATOR) : undefined;
  const filtersWithoutOrder = {
    group,
    user,
    type,
    cloudRegionId,
    ...periodInfo
  };
  const filters = {
    ...filtersWithoutOrder,
    order: {
      metric: getBillingGroupingSortField(metrics),
      aggregate: getBillingGroupingOrderAggregate(aggregate)
    }
  };
  let filterBy = GetBillingData.FILTER_BY.storages;
  let storageType;
  const loadCostDetails = /^object$/i.test(type);
  let StorageRequest = GetGroupedStoragesWithPrevious;
  let StorageTableRequest = GetGroupedStorages;
  let tiersRequest;
  if (/^file$/i.test(type)) {
    storageType = 'FILE_STORAGE';
    filterBy = GetBillingData.FILTER_BY.fileStorages;
    StorageRequest = GetGroupedFileStoragesWithPrevious;
    StorageTableRequest = GetGroupedFileStorages;
  } else if (/^object$/i.test(type)) {
    storageType = 'OBJECT_STORAGE';
    filterBy = GetBillingData.FILTER_BY.objectStorages;
    StorageRequest = GetGroupedObjectStoragesWithPrevious;
    StorageTableRequest = GetGroupedObjectStorages;
    tiersRequest = new GetObjectStorageLayersInfo({
      filters: {
        ...filtersWithoutOrder,
        filterBy: GetBillingData.FILTER_BY.objectStorages
      },
      loadCostDetails: true
    });
  }
  const storages = new StorageRequest({
    filters,
    pagination: true,
    loadCostDetails
  });
  const storagesTable = new StorageTableRequest({
    filters,
    pagination: true,
    loadCostDetails
  });
  (storages.fetch)();
  (storagesTable.fetch)();
  const summary = new GetBillingData({
    filters: {
      ...filters,
      filterBy
    },
    loadCostDetails
  });
  (summary.fetch)();

  if (tiersRequest) {
    (preFetchBillingRequest)(tiersRequest);
  }

  return {
    user,
    group,
    type,
    summary,
    storages,
    storagesTable,
    tiersRequest,
    storageType
  };
}

function renderTable (
  {
    storages,
    discounts: discountsFn,
    height,
    aggregate,
    showDetails
  }
) {
  if (!storages || !storages.loaded) {
    return null;
  }
  const storageClass = aggregate && aggregate !== StorageAggregate.default
    ? getStorageClassByAggregate(aggregate)
    : 'TOTAL';
  const storageClassName = aggregate && aggregate !== StorageAggregate.default
    ? getStorageClassNameByAggregate(aggregate)
    : undefined;
  const getLayerValue = (item, key) => {
    const {
      costDetails = {},
      value,
      usage,
      usageLast
    } = item || {};
    if (!showDetails) {
      switch (key) {
        case 'size': return (usageLast || 0);
        case 'avgSize': return (usage || 0);
        case 'cost': return (value || 0);
        default:
          return 0;
      }
    }
    const {
      tiers = {}
    } = costDetails;
    const tier = tiers[storageClass] || {};
    return tier[key] || 0;
  };
  const getLayerValues = (item, ...keys) =>
    keys.map((key) => getLayerValue(item, key)).reduce((r, c) => r + c, 0);
  const getLayerCostValue = (item, ...keys) => {
    const value = getLayerValues(item, ...keys);
    return value || showDetails ? costTickFormatter(value || 0) : null;
  };
  const getLayerSizeValue = (item, ...keys) => {
    const value = getLayerValues(item, ...keys);
    return value || showDetails ? numberFormatter(value || 0) : null;
  };
  const getDetailedCellsTitle = (title, measure) => {
    const details = [
      measure,
      storageClassName
    ].filter(Boolean);
    if (details.length > 0) {
      return `${title} (${details.join(', ')})`;
    }
    return title;
  };
  const getDetailedCells = ({
    title,
    measure,
    key = (title || '').toLowerCase(),
    currentKey,
    oldVersionsKey,
    dataExtractor = ((item, ...keys) => 0)
  }) => ([
    {
      key,
      title: getDetailedCellsTitle(title, measure),
      headerSpan: showDetails ? 2 : 1,
      render: (item) => {
        const total = dataExtractor(item, currentKey, oldVersionsKey);
        return (
          <span>
            {total}
          </span>
        );
      },
      className: showDetails
        ? classNames(styles.cell, styles.rightAlignedCell, styles.noPadding)
        : styles.cell,
      headerClassName: showDetails
        ? classNames(styles.cell, styles.centeredCell)
        : styles.cell
    },
    showDetails ? ({
      key: `${key}-old-versions`,
      title: '\u00A0',
      header: false,
      render: (item) => {
        const oldVersions = dataExtractor(item, oldVersionsKey);
        return (
          <span className="cp-text-not-important">
            <span>{'/ '}</span>
            {oldVersions}
          </span>
        );
      },
      className: classNames(styles.cell, styles.leftAlignedCell, styles.noPadding)
    }) : undefined
  ].filter(Boolean));
  const columns = [
    {
      key: 'storage',
      title: 'Storage',
      className: styles.storageCell,
      render: ({info, name}) => {
        return info && info.name ? info.pathMask || info.name : name;
      },
      fixed: true
    },
    {
      key: 'owner',
      title: 'Owner',
      dataIndex: 'owner',
      render: owner => (<DisplayUser userName={owner} />),
      className: styles.cell
    },
    {
      key: 'billingCenter',
      title: 'Billing Center',
      dataIndex: 'billingCenter',
      className: styles.cell
    },
    {
      key: 'storageType',
      title: 'Type',
      dataIndex: 'storageType',
      className: styles.cell
    },
    ...getDetailedCells({
      title: 'Cost',
      dataExtractor: getLayerCostValue,
      currentKey: 'cost',
      oldVersionsKey: 'oldVersionCost'
    }),
    ...getDetailedCells({
      title: 'Avg. Vol.',
      measure: 'GB',
      dataExtractor: getLayerSizeValue,
      currentKey: 'avgSize',
      oldVersionsKey: 'oldVersionAvgSize'
    }),
    ...getDetailedCells({
      title: 'Cur. Vol.',
      measure: 'GB',
      dataExtractor: getLayerSizeValue,
      currentKey: 'size',
      oldVersionsKey: 'oldVersionSize'
    }),
    {
      key: 'region',
      title: 'Region',
      dataIndex: 'region',
      className: styles.cell
    },
    {
      key: 'provider',
      title: 'Provider',
      dataIndex: 'provider',
      className: styles.cell
    },
    {
      key: 'created',
      title: 'Created date',
      dataIndex: 'created',
      render: (value) => value ? moment.utc(value).format('DD MMM YYYY') : value,
      className: styles.cell
    }
  ];
  const dataSource = Object.values(
    discounts.applyGroupedDataDiscounts(storages.value || {}, discountsFn)
  );
  console.log(dataSource);
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
    <div
      className={styles.storageReport}
    >
      <div
        className={
          classNames(
            styles.storageReportTableContainer,
            'cp-bordered'
          )
        }
        style={{
          maxHeight: height - (paginationEnabled ? 40 : 10)
        }}
      >
        <table
          className={classNames('cp-report-table', styles.storageReportTable)}
        >
          <thead>
            <tr>
              {
                columns
                  .filter((column) => column.header === undefined || column.header)
                  .map((column, index) => (
                    <th
                      key={column.key}
                      className={
                        classNames(
                          column.headerClassName || column.className,
                          styles.cell,
                          styles.fixedRow,
                          {
                            [styles.fixedColumn]: index === 0,
                            'fixed-column': index === 0
                          }
                        )
                      }
                      colSpan={column.headerSpan || 1}
                    >
                      {column.title}
                    </th>
                  ))
              }
            </tr>
          </thead>
          <tbody>
            {
              dataSource.map((item, index) => (
                <tr
                  key={`item-${index}`}
                  className={getRowClassName(item)}
                >
                  {
                    columns.map((column) => (
                      <td
                        key={column.key}
                        className={
                          classNames(
                            column.className,
                            styles.cell,
                            {
                              [styles.fixedColumn]: !!column.fixed,
                              'fixed-column': !!column.fixed
                            }
                          )
                        }
                      >
                        {
                          column.render
                            ? column.render(column.dataIndex ? item[column.dataIndex] : item, item)
                            : item[column.dataIndex]
                        }
                      </td>
                    ))
                  }
                </tr>
              ))
            }
          </tbody>
        </table>
        {
          /*
          <Table
          className={classNames('cp-report-table', styles.storageReportTable)}
          rowClassName={getRowClassName}
          rowKey={({info, name}) => {
            return info && info.id ? `storage_${info.id}` : `storage_${name}`;
          }}
          loading={storages.pending}
          dataSource={dataSource}
          columns={columns}
          pagination={false}
          size="small"
          scroll={{
            x: width,
            y: height - (paginationEnabled ? 30 : 0) - 50
          }}
        />
           */
        }
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
              disabled={storages.pending}
              current={storages.pageNum + 1}
              pageSize={storages.pageSize}
              total={storages.totalPages * storages.pageSize}
              onChange={(page) => storages.fetchPage(page - 1)}
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

  onSelectLayer = ({key}) => {
    const {
      filters = {}
    } = this.props;
    const {
      storageAggregate,
      storageAggregateNavigation
    } = filters;
    if (typeof storageAggregateNavigation === 'function') {
      storageAggregateNavigation(
        storageAggregate === key
          ? StorageAggregate.default
          : key
      );
    }
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
    const {
      type,
      filters = {}
    } = this.props;
    if (/^file$/i.test(type)) {
      return 'File storages';
    }
    if (/^object$/i.test(type)) {
      const {
        storageAggregate
      } = filters;
      if (!storageAggregate || storageAggregate === StorageAggregate.default) {
        return 'Object storages';
      }
      const name = getStorageClassNameByAggregate(storageAggregate);
      return `${name} object storages`;
    }
    return 'Storages';
  };

  @computed
  get layersMock () {
    const {
      filters = {},
      tiersRequest
    } = this.props;
    let data = {};
    const {
      metrics
    } = filters;
    if (tiersRequest && tiersRequest.loaded) {
      const [storage = {}] = Object.values(tiersRequest.value || []);
      if (storage.costDetails && storage.costDetails.tiers) {
        data = storage.costDetails.tiers;
      }
    }
    const labels = Object.keys(data)
      .filter((storageClass) => DEFAULT_STORAGE_CLASS_ORDER.includes(storageClass))
      .sort((a, b) => {
        const aIndex = DEFAULT_STORAGE_CLASS_ORDER.indexOf(a);
        const bIndex = DEFAULT_STORAGE_CLASS_ORDER.indexOf(b);
        return aIndex - bIndex;
      });
    const aggregates = labels.map(label => getAggregateByStorageClass(label));
    const getData = (key, labels) => {
      const result = [];
      labels.forEach((label) => {
        result.push((data[label] || {})[key] || 0);
      });
      return result;
    };
    const filter = metrics === StorageMetrics.volume
      ? ['abgSize', 'oldVersionAvgSize']
      : ['cost', 'oldVersionCost'];
    const datasets = filter
      .map(key => {
        return {
          label: key,
          data: getData(key, labels)
        };
      });
    return {
      aggregates,
      labels: labels.map(getStorageClassName),
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
      reportThemes,
      tiersRequest
    } = this.props;
    const {
      period,
      range,
      region: cloudRegionId,
      storageAggregate,
      metrics
    } = filters;
    const costsUsageSelectorHeight = 30;
    const tiersPending = tiersRequest && tiersRequest.pending;
    const tiersData = this.layersMock;
    const valueFormatter = metrics === StorageMetrics.volume
      ? numberFormatter
      : costTickFormatter;
    const topDescription = metrics === StorageMetrics.volume
      ? 'by volume'
      : undefined;
    const showTableDetails = /^object$/i.test(type);
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
                {/^object$/i.test(type) && tiersRequest ? (
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
                                <StorageFilter />
                              </div>
                              <StorageLayers
                                highlightedLabel={
                                  tiersData.aggregates.indexOf(storageAggregate)
                                }
                                loading={tiersPending}
                                onSelect={this.onSelectLayer}
                                data={tiersData}
                                title={'Object storage layers'}
                                style={{height: height - costsUsageSelectorHeight}}
                                valueFormatter={valueFormatter}
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
                            {
                              !/^object$/i.test(type) && (
                                <div
                                  style={{
                                    display: 'flex',
                                    flexDirection: 'row',
                                    justifyContent: 'center',
                                    alignItems: 'center',
                                    height: costsUsageSelectorHeight
                                  }}
                                >
                                  <StorageFilter />
                                </div>
                              )
                            }
                            <BarChart
                              request={storages}
                              discounts={storageDiscounts}
                              title={this.getTitle()}
                              top={tablePageSize}
                              topDescription={topDescription}
                              style={{height: height - costsUsageSelectorHeight}}
                              dataSample={
                                StorageFilters[this.state.dataSampleKey].dataSample
                              }
                              previousDataSample={
                                StorageFilters[this.state.dataSampleKey].previousDataSample
                              }
                              valueFormatter={valueFormatter}
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
                            aggregate={storageAggregate}
                            showDetails={showTableDetails}
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
