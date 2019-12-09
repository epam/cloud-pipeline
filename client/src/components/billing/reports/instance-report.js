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
import {Table, Tooltip} from 'antd';
import {
  BarChart,
  BillingTable,
  colors,
  Summary
} from './charts';
import {Period, getPeriod} from './periods';
import InstanceFilter, {InstanceFilters} from './filters/instance-filter';
import {GetBillingData, GetGroupedBillingData} from '../../../models/billing';
import {ChartContainer} from './utilities';
import styles from './reports.css';

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
  const instances = new GetGroupedBillingData(
    filters,
    GetGroupedBillingData.GROUP_BY.instances
  );
  instances.fetch();
  const tools = new GetGroupedBillingData(
    filters,
    GetGroupedBillingData.GROUP_BY.tools
  );
  tools.fetch();
  const pipelines = new GetGroupedBillingData(
    filters,
    GetGroupedBillingData.GROUP_BY.pipelines
  );
  pipelines.fetch();
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
    tools,
    pipelines
  };
}

function renderResourcesSubData (
  {
    data,
    dataSample = InstanceFilters.value.dataSample,
    previousDataSample = InstanceFilters.value.previousDataSample,
    color = colors.orange,
    owner = true,
    title,
    singleTitle
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
      title: 'Usage (hours)',
      render: value => value ? `${Math.round(value)}` : null
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
    <div>
      <div className={styles.resourcesChart}>
        <BarChart
          data={data}
          dataSample={dataSample}
          previousDataSample={previousDataSample}
          title={title}
          style={{height: 250}}
          colors={{
            current: {background: color, color: color},
            previous: {background: colors.blue, color: colors.blue}
          }}
        />
      </div>
      <div className={styles.resourcesTable}>
        <Table
          dataSource={Object.values(data)}
          rowKey={({name, value, usage}) => {
            return `${name}_${value}_${usage}`;
          }}
          columns={columns}
          size="small"
        />
      </div>
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
  handleDataSampleChange = (dataSample, previousDataSample) => {
    this.setState({dataSample, previousDataSample});
  };

  render () {
    const {
      summary,
      instances,
      tools,
      pipelines,
      type
    } = this.props;
    const {dataSample, previousDataSample} = this.state;
    return (
      <div className={styles.chartsContainer}>
        <div className={styles.chartsColumnContainer}>
          <ChartContainer>
            <BillingTable
              data={summary && summary.loaded ? summary.value : null}
              showQuota={false}
            />
            <Summary
              data={summary && summary.loaded ? summary.value.values : []}
              title={this.getSummaryTitle()}
              colors={{
                previous: {color: colors.yellow},
                current: {color: colors.green}
              }}
              style={{height: 500}}
            />
          </ChartContainer>
        </div>
        <div className={styles.chartsColumnContainer}>
          <ChartContainer>
            <InstanceFilter
              onChange={this.handleDataSampleChange}
              value={dataSample}
              previous={previousDataSample}
            />
            <ResourcesSubData
              data={instances && instances.loaded ? instances.value : []}
              dataSample={dataSample}
              previousDataSample={previousDataSample}
              owner={false}
              color={colors.orange}
              title={this.getInstanceTitle()}
              singleTitle="Instance"
            />
            <ResourcesSubData
              data={tools && tools.loaded ? tools.value : []}
              dataSample={dataSample}
              previousDataSample={previousDataSample}
              owner
              color={colors.gray}
              title="Tools"
              singleTitle="Tool"
            />
            <ResourcesSubData
              data={pipelines && pipelines.loaded ? pipelines.value : []}
              dataSample={dataSample}
              previousDataSample={previousDataSample}
              owner
              color={colors.current}
              title="Pipelines"
              singleTitle="Pipeline"
            />
          </ChartContainer>
        </div>
      </div>
    );
  }
}

export default inject('awsRegions')(inject(injection)(observer(InstanceReport)));
