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
import {computed} from 'mobx';
import {Icon, Button} from 'antd';
import {
  BarChart,
  BillingTable,
  Summary,
  StorageLayers
} from './charts';
import {
  costTickFormatter,
  numberFormatter,
  ResizableContainer
} from './utilities';
import {fadeout} from '../../../themes/utilities/color-utilities';
import BillingNavigation, {RUNNER_SEPARATOR, REGION_SEPARATOR} from '../navigation';
import {
  getBillingGroupingSortField,
  parseStorageMetrics,
  StorageMetrics
} from '../navigation/metrics';
import {Period, getPeriod} from '../../special/periods';
import StorageFilter, {StorageFilters} from './filters/storage-filter';
import Export from './export';
import Discounts from './discounts';
import {
  GetBillingData,
  GetGroupedStorages,
  GetGroupedStoragesWithPrevious,
  GetGroupedFileStorages,
  GetGroupedFileStoragesWithPrevious,
  GetGroupedObjectStorages,
  GetGroupedObjectStoragesWithPrevious,
  GetObjectStorageLayersInfo,
  preFetchBillingRequest,
  LAYERS_KEYS
} from '../../../models/billing';
import {StorageReportLayout, Layout} from './layout';
import {
  getAggregateByStorageClass,
  getBillingGroupingOrderAggregate,
  getStorageClassName,
  getStorageClassNameByAggregate,
  parseStorageAggregate,
  StorageAggregate,
  DEFAULT_STORAGE_CLASS_ORDER,
  getStorageClassByAggregate
} from '../navigation/aggregate';
import {
  getDetailsDatasetsByStorageClassAndMetrics, getItemDetailsByMetrics,
  getSummaryDatasetsByStorageClass
} from './charts/object-storage/get-datasets-by-storage-class';
import StorageTable from './storage-table';

const tablePageSize = 10;

const LAYERS_LABELS = {
  [LAYERS_KEYS.avgSize]: 'Average size',
  [LAYERS_KEYS.oldVersionAvgSize]: 'Old versions average size',
  [LAYERS_KEYS.cost]: 'Cost',
  [LAYERS_KEYS.oldVersionCost]: 'Old versions cost'
};

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
      ...filtersWithoutOrder,
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
        !key || storageAggregate === key
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
  get tiersData () {
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
      ? [LAYERS_KEYS.avgSize, LAYERS_KEYS.oldVersionAvgSize]
      : [LAYERS_KEYS.cost, LAYERS_KEYS.oldVersionCost];
    const datasets = filter
      .map(key => {
        return {
          stack: 'details',
          details: true,
          label: LAYERS_LABELS[key] || key,
          data: getData(key, labels),
          isOldVersions: [LAYERS_KEYS.oldVersionCost, LAYERS_KEYS.oldVersionAvgSize].includes(key),
          showDataLabel: (value, datasetValues, options) => {
            const {
              getPxSize = (() => 0)
            } = options || {};
            const detailsDatasets = datasetValues
              .filter((value) => value.dataset.details &&
                (value.value > 0 && getPxSize(value.value) > 5.0)
              );
            return detailsDatasets.length > 1;
          }
        };
      });
    const totalDataset = (datasets || []).reduce((acc, current) => {
      acc.data = current.data.map((value, index) => value + (acc.data[index] || 0));
      return acc;
    }, {
      data: [],
      label: 'Total',
      dataItemTitle: 'Total',
      hidden: true,
      showDataLabel: true,
      showFlag: true
    });
    return {
      aggregates,
      labels: labels.map(getStorageClassName),
      datasets: [totalDataset, ...datasets]
    };
  }

  @computed
  get summaryDatasets () {
    const {
      type,
      filters = {}
    } = this.props;
    const {
      storageAggregate
    } = filters;
    if (!/^object$/i.test(type)) {
      return undefined;
    }
    const total = !storageAggregate || storageAggregate === StorageAggregate.default;
    const storageClass = total
      ? 'TOTAL'
      : getStorageClassByAggregate(storageAggregate);
    return getSummaryDatasetsByStorageClass(storageClass);
  }

  @computed
  get detailsDatasets () {
    const {
      type,
      filters = {}
    } = this.props;
    const {
      storageAggregate,
      metrics
    } = filters;
    if (!/^object$/i.test(type)) {
      return getDetailsDatasetsByStorageClassAndMetrics(undefined, metrics);
    }
    const total = !storageAggregate || storageAggregate === StorageAggregate.default;
    const storageClass = total
      ? 'TOTAL'
      : getStorageClassByAggregate(storageAggregate);
    return getDetailsDatasetsByStorageClassAndMetrics(storageClass, metrics);
  }

  @computed
  get extraTooltipForItemCallback () {
    const {
      type,
      filters = {}
    } = this.props;
    const {
      metrics
    } = filters;
    if (!/^object$/i.test(type)) {
      return () => undefined;
    }
    return (dataItem) => getItemDetailsByMetrics(dataItem, metrics);
  }

  renderSelectedLayerButton = () => {
    const {filters} = this.props;
    const {storageAggregate} = filters;
    const layer = this.tiersData.labels[this.tiersData.aggregates.indexOf(storageAggregate)];
    if (layer) {
      return (
        <Button
          size="small"
          onClick={() => this.onSelectLayer({})}
          style={{
            position: 'absolute',
            top: 5,
            right: 5
          }}
        >
          <Icon type="close" /> {layer}
        </Button>
      );
    }
    return null;
  };

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
    const tiersData = this.tiersData;
    const isVolumeMetrics = metrics === StorageMetrics.volume;
    const valueFormatter = isVolumeMetrics
      ? numberFormatter
      : costTickFormatter;
    const topDescription = isVolumeMetrics
      ? 'GB'
      : undefined;
    const showTableDetails = /^object$/i.test(type);
    const selectedIndex = tiersData.aggregates.indexOf(storageAggregate);
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
                            datasets={this.summaryDatasets}
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
                                  height: costsUsageSelectorHeight,
                                  position: 'relative'
                                }}
                              >
                                <StorageFilter />
                                {this.renderSelectedLayerButton()}
                              </div>
                              <StorageLayers
                                highlightedLabel={selectedIndex}
                                loading={tiersPending}
                                onSelect={this.onSelectLayer}
                                data={tiersData}
                                title={
                                  ['Object storage layers', topDescription]
                                    .filter(Boolean).join(', ')
                                }
                                style={{height: height - costsUsageSelectorHeight}}
                                valueFormatter={valueFormatter}
                                highlightTickFn={
                                  (_, tick) => tick._index === selectedIndex
                                }
                                highlightTickStyle={{
                                  fontColor: reportThemes.textColor,
                                  fontSize: 14
                                }}
                                highlightAxisStyle={{
                                  backgroundColor: fadeout(reportThemes.lightBlue, 0.90)
                                }}
                                discounts={isVolumeMetrics ? undefined : storageDiscounts}
                              />
                            </div>
                          )
                        }
                      </ResizableContainer>
                    </Layout.Panel>
                  </div>
                ) : null}
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
                              datasets={this.detailsDatasets}
                              valueFormatter={valueFormatter}
                              highlightTickFn={
                                (storage) => `${(storage.groupingInfo || {}).is_deleted}` === 'true'
                              }
                              highlightTickStyle={{
                                fontColor: reportThemes.errorColor
                              }}
                              extraTooltipForItem={this.extraTooltipForItemCallback}
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
                          <StorageTable
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
