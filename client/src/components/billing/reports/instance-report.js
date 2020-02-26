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
import {
  GetBillingData,
  GetGroupedBillingData,
  GetGroupedBillingDataPaginated,
  GetGroupedBillingDataWithPreviousPaginated
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
  const instances = new GetGroupedBillingDataWithPreviousPaginated(
    filters,
    GetGroupedBillingData.GROUP_BY.instances,
    tablePageSize,
    0
  );
  instances.fetch();
  const instancesTable = new GetGroupedBillingDataPaginated(
    filters,
    GetGroupedBillingData.GROUP_BY.instances,
    tablePageSize,
    0
  );
  instancesTable.fetch();
  const tools = new GetGroupedBillingDataWithPreviousPaginated(
    filters,
    GetGroupedBillingData.GROUP_BY.tools,
    tablePageSize,
    0
  );
  tools.fetch();
  const toolsTable = new GetGroupedBillingDataPaginated(
    filters,
    GetGroupedBillingData.GROUP_BY.tools,
    tablePageSize,
    0
  );
  toolsTable.fetch();
  const pipelines = new GetGroupedBillingDataWithPreviousPaginated(
    filters,
    GetGroupedBillingData.GROUP_BY.pipelines,
    tablePageSize,
    0
  );
  pipelines.fetch();
  const pipelinesTable = new GetGroupedBillingDataPaginated(
    filters,
    GetGroupedBillingData.GROUP_BY.pipelines,
    tablePageSize,
    0
  );
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
    data,
    tableDataRequest,
    chartError = null,
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
          data={data}
          error={chartError}
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
    return (
      <div className={styles.chartsContainer}>
        <ResourcesDataBlock>
          <BillingTable summary={summary} showQuota={false} />
          <Summary
            summary={summary}
            quota={false}
            title={this.getSummaryTitle()}
            style={{flex: 1, maxHeight: 500}}
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
          data={instances && instances.loaded ? instances.value : []}
          chartError={instances && instances.error ? instances.error : null}
          tableDataRequest={instancesTable}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          owner={false}
          title={this.getInstanceTitle()}
          singleTitle="Instance"
        />
        <ResourcesSubData
          data={pipelines && pipelines.loaded ? pipelines.value : []}
          chartError={pipelines && pipelines.error ? pipelines.error : null}
          tableDataRequest={pipelinesTable}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          owner
          title="Pipelines"
          singleTitle="Pipeline"
        />
        <ResourcesSubData
          data={tools && tools.loaded ? tools.value : []}
          chartError={tools && tools.error ? tools.error : null}
          tableDataRequest={toolsTable}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          owner
          title="Tools"
          singleTitle="Tool"
        />
      </div>
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
