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
import {Button, Icon, Pagination, Table, Tooltip} from 'antd';
import {
  BarChart,
  BillingTable,
  DetailsChart,
  Summary
} from './charts';
import BillingNavigation, {RUNNER_SEPARATOR, REGION_SEPARATOR} from '../navigation';
import {Period, getPeriod} from '../../special/periods';
import InstanceFilter, {InstanceFilters, getSummaryDatasets} from './filters/instance-filter';
import Discounts, {discounts} from './discounts';
import Export from './export';
import {
  GetBillingData,
  GetGroupedInstances,
  GetGroupedInstancesWithPrevious,
  GetGroupedTools,
  GetGroupedToolsWithPrevious,
  GetGroupedPipelines,
  GetGroupedPipelinesWithPrevious,
  GetInstanceCostsDetailsInfo,
  preFetchBillingRequest
} from '../../../models/billing';
import {
  numberFormatter,
  costTickFormatter,
  DisplayUser,
  ResizableContainer
} from './utilities';
import {
  getInstanceBillingOrderAggregateField,
  getInstanceBillingOrderMetricsField,
  getInstanceMetricsName,
  InstanceMetrics,
  parseInstanceMetrics
} from '../navigation/metrics';
import {InstanceReportLayout, Layout} from './layout';
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
    region: regionQ,
    metrics: metricsQ
  } = location.query;
  const metrics = parseInstanceMetrics(metricsQ);
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
  const orderMetrics = getInstanceBillingOrderMetricsField(metrics);
  const orderAggregate = getInstanceBillingOrderAggregateField(metrics);
  const filters = {
    ...filtersWithoutOrder,
    order: orderAggregate || orderMetrics
      ? {metric: orderMetrics, aggregate: orderAggregate}
      : undefined
  };
  const pagination = {
    pageSize: tablePageSize,
    pageNum: 0
  };
  const instances = new GetGroupedInstancesWithPrevious({filters, pagination});
  instances.fetch();
  const instancesTable = new GetGroupedInstances({filters, pagination});
  instancesTable.fetch();
  const tools = new GetGroupedToolsWithPrevious({filters, pagination});
  tools.fetch();
  const toolsTable = new GetGroupedTools({filters, pagination});
  toolsTable.fetch();
  const pipelines = new GetGroupedPipelinesWithPrevious({filters, pagination});
  pipelines.fetch();
  const pipelinesTable = new GetGroupedPipelines({filters, pagination});
  pipelinesTable.fetch();
  const filterBy = {
    resourceType: GetBillingData.FILTER_BY.compute
  };
  if (/^cpu$/i.test(type)) {
    filterBy.computeType = GetBillingData.FILTER_BY.cpu;
  }
  if (/^gpu$/i.test(type)) {
    filterBy.computeType = GetBillingData.FILTER_BY.gpu;
  }

  const summary = new GetBillingData({filters: {...filtersWithoutOrder, filterBy}});
  const costDetails = new GetInstanceCostsDetailsInfo({
    filters: {
      ...filtersWithoutOrder,
      filterBy
    }
  });
  costDetails.fetch();
  summary.fetch();
  (preFetchBillingRequest)(costDetails);
  return {
    user,
    group,
    type,
    summary,
    instances,
    instancesTable,
    tools,
    toolsTable,
    pipelines,
    pipelinesTable,
    costDetails,
    metrics
  };
}

function renderResourcesSubData (
  {
    request,
    discounts: discountsFn,
    tableDataRequest,
    metrics,
    owner = true,
    title,
    singleTitle,
    extra,
    extraHeight,
    width,
    height
  }
) {
  const {
    dataSample = 'value',
    previousDataSample = 'previous'
  } = InstanceFilters[metrics] || InstanceFilters[InstanceMetrics.costs] || {};
  const isCosts = [
    InstanceMetrics.costs,
    InstanceMetrics.computeCosts,
    InstanceMetrics.diskCosts,
    undefined
  ].includes(metrics);
  const columns = [
    {
      key: 'name',
      dataIndex: 'name',
      title: singleTitle,
      className: styles.tableCell,
      render: (value, {fullName = null}) => {
        if (fullName) {
          return (
            <Tooltip
              title={fullName}
              overlayStyle={{wordWrap: 'break-word'}}
            >
              {value}
            </Tooltip>
          );
        }

        return value;
      }
    },
    owner && {
      key: 'owner',
      dataIndex: 'owner',
      title: 'Owner',
      className: styles.tableCell,
      render: owner => (<DisplayUser userName={owner} />)
    },
    {
      key: 'usage',
      dataIndex: 'usage',
      title: 'Usage (hours)',
      className: styles.tableCell
    },
    {
      key: 'runs',
      dataIndex: 'runsCount',
      title: 'Runs count',
      className: styles.tableCell,
      render: value => value ? `${Math.round(value)}` : null
    },
    {
      key: 'cost',
      dataIndex: 'value',
      title: 'Cost',
      className: styles.tableCell,
      render: value => costTickFormatter(value)
    },
    {
      key: 'compute-cost',
      title: 'Compute cost',
      className: styles.tableCell,
      render: (item) => item && item.costDetails && item.costDetails.computeCost
        ? costTickFormatter(item.costDetails.computeCost)
        : null
    },
    {
      key: 'disk-cost',
      title: 'Disk cost',
      className: styles.tableCell,
      render: (item) => item && item.costDetails && item.costDetails.diskCost
        ? costTickFormatter(item.costDetails.diskCost)
        : null
    }
  ].filter(Boolean);
  const tableData = tableDataRequest && tableDataRequest.loaded
    ? discounts.applyGroupedDataDiscounts(tableDataRequest.value, discountsFn)
    : {};
  const heightCorrected = extra && extraHeight ? (height - extraHeight) / 2.0 : height / 2.0;
  const dataSource = Object.values(tableData);
  const paginationEnabled = tableDataRequest && tableDataRequest.loaded
    ? tableDataRequest.totalPages > 1
    : false;
  return (
    <div style={{width, height}}>
      {extra}
      <BarChart
        request={request}
        discounts={discountsFn}
        datasets={[{sample: dataSample}, {sample: previousDataSample, isPrevious: true}]}
        title={title}
        style={{height: heightCorrected}}
        top={tablePageSize}
        valueFormatter={isCosts ? costTickFormatter : numberFormatter}
      />
      <div
        style={{
          position: 'relative',
          overflow: 'auto',
          maxHeight: heightCorrected - (paginationEnabled ? 30 : 0),
          padding: 5
        }}
      >
        <Table
          className={styles.resourcesTable}
          dataSource={dataSource}
          loading={tableDataRequest.pending}
          rowKey={({name, value, usage}) => {
            return `${name}_${value}_${usage}`;
          }}
          columns={columns}
          pagination={false}
          rowClassName={() => styles.resourcesTableRow}
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
              current={tableDataRequest.pageNum + 1}
              pageSize={tableDataRequest.pageSize}
              total={tableDataRequest.totalPages * tableDataRequest.pageSize}
              onChange={async (page) => {
                await tableDataRequest.fetchPage(page - 1);
              }}
              size="small"
            />
          </div>
        )
      }
    </div>
  );
}

const ResourcesSubData = observer(renderResourcesSubData);

class InstanceReport extends React.Component {
  get costDetailsData () {
    const {costDetails} = this.props;
    if (costDetails.loaded) {
      const {
        computeCost,
        diskCost
      } = costDetails.value;
      return {
        aggregates: [InstanceMetrics.computeCosts, InstanceMetrics.diskCosts],
        datasets: [
          {
            data: [computeCost, diskCost],
            label: 'Cost'
          }
        ],
        labels: ['Compute', 'Disk']
      };
    }
    return {
      aggregates: [InstanceMetrics.computeCosts, InstanceMetrics.diskCosts],
      datasets: [
        {
          data: [undefined, undefined],
          label: 'Cost'
        }
      ],
      labels: ['Compute', 'Disk']
    };
  }

  get summaryDatasets () {
    const {
      metrics
    } = this.props;
    return getSummaryDatasets(metrics);
  }

  getInstanceTitle = () => {
    const {type} = this.props;
    if (/^cpu$/i.test(type)) {
      return 'CPU instance types';
    }
    if (/^gpu$/i.test(type)) {
      return 'GPU instance types';
    }
    return 'Instance types';
  };
  getSummaryTitle = () => {
    const {type} = this.props;
    if (/^cpu$/i.test(type)) {
      return 'CPU instances runs';
    }
    if (/^gpu$/i.test(type)) {
      return 'GPU instances runs';
    }
    return 'Compute instances runs';
  };
  getClarificationTitle = () => {
    const {
      metrics
    } = this.props;
    if (InstanceFilters && InstanceFilters[metrics]) {
      const {title} = InstanceFilters[metrics];
      return title ? `- ${title}` : '';
    }
    return '';
  }
  handleMetricsChange = (newMetrics) => {
    const {
      filters = {}
    } = this.props;
    const {
      metricsNavigation
    } = filters;
    if (typeof metricsNavigation === 'function') {
      metricsNavigation(newMetrics || InstanceMetrics.costs);
    }
  };

  handleCostDetailsChange = ({key}) => {
    const {
      metrics
    } = this.props;
    if (key === metrics) {
      this.handleMetricsChange(InstanceMetrics.costs);
    } else {
      this.handleMetricsChange(key);
    }
  }

  render () {
    const {
      summary,
      instances,
      tools,
      pipelines,
      instancesTable,
      toolsTable,
      pipelinesTable,
      user,
      group,
      filters = {},
      type: computeType,
      metrics,
      costDetails
    } = this.props;
    const costDetailsPending = costDetails.pending && !costDetails.loaded;
    const selectedCostDetailsMetricsIndex = [
      InstanceMetrics.computeCosts,
      InstanceMetrics.diskCosts
    ].indexOf(metrics);
    const {period, range, region: cloudRegionId} = filters;
    return (
      <Discounts.Consumer>
        {
          (computeDiscounts) => (
            <Export.Consumer
              exportConfiguration={{
                types: [
                  'INSTANCE',
                  'PIPELINE',
                  'TOOL'
                ],
                user,
                group,
                period,
                range,
                filters: {
                  compute_type: computeType ? [computeType.toUpperCase()] : undefined,
                  cloudRegionId: cloudRegionId &&
                  cloudRegionId.length > 0
                    ? cloudRegionId
                    : undefined
                }
              }}
            >
              <Layout
                layout={InstanceReportLayout.Layout}
                gridStyles={InstanceReportLayout.GridStyles}
              >
                <div key={InstanceReportLayout.Panels.summary}>
                  <Layout.Panel
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      minHeight: 0
                    }}
                  >
                    <BillingTable
                      compute={summary}
                      computeDiscounts={computeDiscounts}
                      showQuota={false}
                    />
                    <ResizableContainer style={{flex: 1}}>
                      {
                        ({width, height}) => (
                          <Summary
                            compute={summary}
                            computeDiscounts={computeDiscounts}
                            quota
                            datasets={this.summaryDatasets}
                            title={this.getSummaryTitle()}
                            style={{width, height}}
                          />
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
                <div key={InstanceReportLayout.Panels.details}>
                  <Layout.Panel>
                    <ResizableContainer style={{width: '100%', height: '100%'}}>
                      {
                        ({height}) => (
                          <div
                            style={{
                              height,
                              position: 'relative'
                            }}
                          >
                            <DetailsChart
                              highlightedLabel={selectedCostDetailsMetricsIndex}
                              loading={costDetailsPending}
                              onSelect={this.handleCostDetailsChange}
                              data={this.costDetailsData}
                              title="Cost details"
                              valueFormatter={costTickFormatter}
                              highlightTickFn={
                                (_, tick) => tick._index === selectedCostDetailsMetricsIndex
                              }
                              discounts={computeDiscounts}
                              showTotal={false}
                            />
                            {
                              selectedCostDetailsMetricsIndex >= 0 && (
                                <Button
                                  size="small"
                                  onClick={() => this.handleMetricsChange(InstanceMetrics.costs)}
                                  style={{
                                    position: 'absolute',
                                    top: 5,
                                    right: 5
                                  }}
                                >
                                  <Icon type="close" /> {getInstanceMetricsName(metrics)}
                                </Button>
                              )
                            }
                          </div>
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
                <div key={InstanceReportLayout.Panels.instances}>
                  <Layout.Panel>
                    <ResizableContainer style={{width: '100%', height: '100%'}}>
                      {
                        ({width, height}) => (
                          <ResourcesSubData
                            extra={(
                              <div
                                style={{
                                  display: 'flex',
                                  flexDirection: 'row',
                                  justifyContent: 'center',
                                  alignItems: 'center',
                                  height: 30
                                }}
                              >
                                <InstanceFilter
                                  onChange={this.handleMetricsChange}
                                  value={metrics}
                                />
                              </div>
                            )}
                            extraHeight={30}
                            request={instances}
                            discounts={computeDiscounts}
                            tableDataRequest={instancesTable}
                            metrics={metrics}
                            owner={false}
                            title={`${this.getInstanceTitle()} ${this.getClarificationTitle()}`}
                            singleTitle="Instance"
                            width={width}
                            height={height}
                          />
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
                <div key={InstanceReportLayout.Panels.pipelines}>
                  <Layout.Panel>
                    <ResizableContainer style={{width: '100%', height: '100%'}}>
                      {
                        ({width, height}) => (
                          <ResourcesSubData
                            request={pipelines}
                            discounts={computeDiscounts}
                            tableDataRequest={pipelinesTable}
                            metrics={metrics}
                            owner
                            title={`Pipelines ${this.getClarificationTitle()}`}
                            singleTitle="Pipeline"
                            width={width}
                            height={height}
                          />
                        )
                      }
                    </ResizableContainer>
                  </Layout.Panel>
                </div>
                <div key={InstanceReportLayout.Panels.tools}>
                  <Layout.Panel>
                    <ResizableContainer style={{width: '100%', height: '100%'}}>
                      {
                        ({width, height}) => (
                          <ResourcesSubData
                            request={tools}
                            discounts={computeDiscounts}
                            tableDataRequest={toolsTable}
                            metrics={metrics}
                            owner
                            title={`Tools ${this.getClarificationTitle()}`}
                            singleTitle="Tool"
                            width={width}
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

export default inject('awsRegions', 'reportThemes')(
  inject(injection)(
    BillingNavigation.attach(
      observer(InstanceReport)
    )
  )
);
