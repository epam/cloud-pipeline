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
import {Table, Tooltip} from 'antd';
import {
  BarChart,
  BillingTable,
  Summary
} from './charts';
import Filters from './filters';
import {Period, getPeriod} from './periods';
import InstanceFilter, {InstanceFilters} from './filters/instance-filter';
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
  costTickFormatter
} from './utilities';
import styles from './reports.css';

const tablePageSize = 6;

function injection (stores, props) {
  const {location, params} = props;
  const {type} = params || {};
  const {
    user,
    group,
    period = Period.month,
    range
  } = location.query;
  const periodInfo = getPeriod(period, range);
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

function ResourcesDataBlock ({children}) {
  return (
    <div className={styles.resourcesChartsContainer}>
      <div>
        {children}
      </div>
    </div>
  );
}

function renderResourcesSubData (
  {
    request,
    tableDataRequest,
    dataSample = InstanceFilters.value.dataSample,
    previousDataSample = InstanceFilters.value.previousDataSample,
    owner = true,
    title,
    singleTitle,
    extra
  }
) {
  const columns = [
    {
      key: 'name',
      dataIndex: 'name',
      title: singleTitle,
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
      title: 'Owner'
    },
    {
      key: 'usage',
      dataIndex: 'usage',
      title: 'Usage (hours)'
    },
    {
      key: 'runs',
      dataIndex: 'runsCount',
      title: 'Runs count',
      render: value => value ? `${Math.round(value)}` : null
    },
    {
      key: 'cost',
      dataIndex: 'value',
      title: 'Cost',
      render: value => value ? `$${Math.round(value * 100.0) / 100.0}` : null
    }
  ].filter(Boolean);
  return (
    <ResourcesDataBlock>
      {extra}
      <div className={styles.resourcesChart}>
        <BarChart
          request={request}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          title={title}
          style={{height: 250}}
          top={tablePageSize}
          valueFormatter={
            dataSample === InstanceFilters.value.dataSample
              ? costTickFormatter
              : numberFormatter
          }
        />
      </div>
      <Table
        className={styles.resourcesTable}
        dataSource={
          Object.values(tableDataRequest && tableDataRequest.loaded ? tableDataRequest.value : {})
        }
        loading={tableDataRequest.pending}
        rowKey={({name, value, usage}) => {
          return `${name}_${value}_${usage}`;
        }}
        columns={columns}
        pagination={{
          current: tableDataRequest.pageNum + 1,
          pageSize: tableDataRequest.pageSize,
          total: tableDataRequest.totalPages * tableDataRequest.pageSize,
          onChange: async (page) => {
            await tableDataRequest.fetchPage(page - 1);
          }
        }}
        rowClassName={() => styles.resourcesTableRow}
        size="small"
      />
    </ResourcesDataBlock>
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
      <Export.Consumer
        className={styles.chartsContainer}
        composers={composers}
      >
        <ResourcesDataBlock>
          <BillingTable summary={summary} showQuota={false} />
          <Summary
            summary={summary}
            quota={false}
            title={this.getSummaryTitle()}
            style={{flex: 1, height: 500}}
          />
        </ResourcesDataBlock>
        <ResourcesSubData
          extra={(
            <div style={{display: 'flex', flexDirection: 'row', justifyContent: 'center'}}>
              <InstanceFilter
                onChange={this.handleDataSampleChange}
                value={dataSample}
                previous={previousDataSample}
              />
            </div>
          )}
          request={instances}
          tableDataRequest={instancesTable}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          owner={false}
          title={this.getInstanceTitle()}
          singleTitle="Instance"
        />
        <ResourcesSubData
          request={pipelines}
          tableDataRequest={pipelinesTable}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          owner
          title="Pipelines"
          singleTitle="Pipeline"
        />
        <ResourcesSubData
          request={tools}
          tableDataRequest={toolsTable}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          owner
          title="Tools"
          singleTitle="Tool"
        />
      </Export.Consumer>
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
