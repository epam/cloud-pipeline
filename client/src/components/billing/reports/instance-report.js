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
import {Pagination, Table, Tooltip} from 'antd';
import {
  BarChart,
  BillingTable,
  Summary
} from './charts';
import Filters, {RUNNER_SEPARATOR} from './filters';
import {Period, getPeriod} from './periods';
import InstanceFilter, {InstanceFilters} from './filters/instance-filter';
import Discounts, {discounts} from './discounts';
import Export, {ExportComposers} from './export';
import {
  GetBillingData,
  GetGroupedInstances,
  GetGroupedInstancesWithPrevious,
  GetGroupedTools,
  GetGroupedToolsWithPrevious,
  GetGroupedPipelines,
  GetGroupedPipelinesWithPrevious
} from '../../../models/billing';
import {
  numberFormatter,
  costTickFormatter,
  DisplayUser,
  ResizableContainer
} from './utilities';
import {InstanceReportLayout, Layout} from './layout';
import styles from './reports.css';

const tablePageSize = 6;

function injection (stores, props) {
  const {location, params} = props;
  const {type} = params || {};
  const {
    user: userQ,
    group: groupQ,
    period = Period.month,
    range
  } = location.query;
  const periodInfo = getPeriod(period, range);
  const group = groupQ ? groupQ.split(RUNNER_SEPARATOR) : undefined;
  const user = userQ ? userQ.split(RUNNER_SEPARATOR) : undefined;
  const filters = {
    group,
    user,
    type,
    ...periodInfo
  };
  const pagination = {
    pageSize: tablePageSize,
    pageNum: 0
  };
  const instances = new GetGroupedInstancesWithPrevious(filters, pagination);
  instances.fetch();
  const instancesTable = new GetGroupedInstances(filters, pagination);
  instancesTable.fetch();
  const tools = new GetGroupedToolsWithPrevious(filters, pagination);
  tools.fetch();
  const toolsTable = new GetGroupedTools(filters, pagination);
  toolsTable.fetch();
  const pipelines = new GetGroupedPipelinesWithPrevious(filters, pagination);
  pipelines.fetch();
  const pipelinesTable = new GetGroupedPipelines(filters, pagination);
  pipelinesTable.fetch();
  let filterBy = GetBillingData.FILTER_BY.compute;
  if (/^cpu$/i.test(type)) {
    filterBy = GetBillingData.FILTER_BY.cpu;
  }
  if (/^gpu$/i.test(type)) {
    filterBy = GetBillingData.FILTER_BY.gpu;
  }

  const summary = new GetBillingData({...filters, filterBy});
  summary.fetch();
  return {
    type,
    summary,
    instances,
    instancesTable,
    tools,
    toolsTable,
    pipelines,
    pipelinesTable
  };
}

function renderResourcesSubData (
  {
    request,
    discounts: discountsFn,
    tableDataRequest,
    dataSample = InstanceFilters.value.dataSample,
    previousDataSample = InstanceFilters.value.previousDataSample,
    owner = true,
    title,
    singleTitle,
    extra,
    extraHeight,
    width,
    height
  }
) {
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
      render: value => value ? `$${Math.round(value * 100.0) / 100.0}` : null
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
        dataSample={dataSample}
        previousDataSample={previousDataSample}
        title={title}
        style={{height: heightCorrected}}
        top={tablePageSize}
        valueFormatter={
          dataSample === InstanceFilters.value.dataSample
            ? costTickFormatter
            : numberFormatter
        }
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
  state = {
    dataSample: 'value',
    previousDataSample: 'previous'
  };
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
    const {dataSample} = this.state;
    if (InstanceFilters && InstanceFilters[dataSample]) {
      const {title} = InstanceFilters[dataSample];
      return title ? `- ${title}` : '';
    }
    return '';
  }
  handleDataSampleChange = (dataSample, previousDataSample) => {
    this.setState({dataSample, previousDataSample});
  };

  render () {
    const {
      summary,
      instances,
      tools,
      pipelines,
      instancesTable,
      toolsTable,
      pipelinesTable
    } = this.props;
    const {dataSample, previousDataSample} = this.state;
    const composers = [
      {
        composer: ExportComposers.summaryComposer,
        options: [summary]
      },
      {
        composer: ExportComposers.defaultComposer,
        options: [
          instances,
          {
            usage: 'usage',
            runs_count: 'runsCount'
          }
        ]
      },
      {
        composer: ExportComposers.defaultComposer,
        options: [
          tools,
          {
            owner: 'owner',
            usage: 'usage',
            runs_count: 'runsCount'
          }
        ]
      },
      {
        composer: ExportComposers.defaultComposer,
        options: [
          pipelines,
          {
            owner: 'owner',
            usage: 'usage',
            runs_count: 'runsCount'
          }
        ]
      }
    ];
    return (
      <Discounts.Consumer>
        {
          (computeDiscounts) => (
            <Export.Consumer
              className={styles.chartsContainer}
              composers={composers}
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
                            quota={false}
                            title={this.getSummaryTitle()}
                            style={{width, height}}
                          />
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
                                  onChange={this.handleDataSampleChange}
                                  value={dataSample}
                                  previous={previousDataSample}
                                />
                              </div>
                            )}
                            extraHeight={30}
                            request={instances}
                            discounts={computeDiscounts}
                            tableDataRequest={instancesTable}
                            dataSample={dataSample}
                            previousDataSample={previousDataSample}
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
                            dataSample={dataSample}
                            previousDataSample={previousDataSample}
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
                            dataSample={dataSample}
                            previousDataSample={previousDataSample}
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

export default inject('awsRegions')(
  inject(injection)(
    Filters.attach(
      observer(InstanceReport)
    )
  )
);
