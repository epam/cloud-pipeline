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
import {Table} from 'antd';
import {
  BarChart,
  GroupedBarChart,
  BillingTable,
  Summary
} from './charts';
import {Period, getPeriod} from './periods';
import {
  GetBillingData,
  GetGroupedBillingData,
  GetGroupedBillingDataPaginated
} from '../../../models/billing';
import {ChartContainer} from './utilities';
import styles from './reports.css';
import ReportsRouting from './routing';

function injection (stores, props) {
  const {location} = props;
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
    ...periodInfo
  };
  const billingCentersRequest = new GetGroupedBillingData(
    filters,
    GetGroupedBillingData.GROUP_BY.billingCenters
  );
  billingCentersRequest.fetch();
  let billingCentersTableRequest;
  if (group) {
    billingCentersTableRequest = new GetGroupedBillingDataPaginated(
      filters,
      GetGroupedBillingData.GROUP_BY.billingCenters
    );
    billingCentersTableRequest.fetch();
  }
  const resources = new GetGroupedBillingData(
    filters,
    GetGroupedBillingData.GROUP_BY.resources
  );
  resources.fetch();
  const summary = new GetBillingData(filters);
  summary.fetch();
  const getBarAndNavigate = (currentBar) => {
    if (props.router) {
      const {router} = props;
      const {pathname} = router.location;
      const {search} = router.location;
      const {label, title} = currentBar;
      const reportsPath = ReportsRouting.getPathByChartInfo(label, title);
      if (reportsPath) {
        router.push(`${reportsPath}${search}`);
      } else {
        const query = getQuery(label);
        query.length && router.push(`${pathname}${query}`);
      }
    }
  };
  const getQuery = (label) => {
    const {users, billingCenters} = props;
    const {query} = props.router.location;
    const user = users.value
      .find((item) => item.userName === label);
    const center = billingCenters.value
      .find((item) => item.name === label);
    const period = query.period ? `&period=${query.period}` : '';
    if (user && !center) {
      return `?user=${user.id}${period}`;
    }
    if (center && !user) {
      return `?group=${center.id}${period}`;
    }
    return '';
  };
  return {
    user,
    group,
    summary,
    billingCentersRequest,
    billingCentersTableRequest,
    resources,
    getBarAndNavigate
  };
}

function toValue (value) {
  if (value) {
    return Math.round((+value) * 100.0) / 100.0;
  }
  return null;
}

function toMoneyValue (value) {
  if (value) {
    return `$${Math.round((+value) * 100.0) / 100.0}`;
  }
  return null;
}

function UserReport ({
  billingCentersRequest,
  resources,
  summary,
  getBarAndNavigate
}) {
  return (
    <div className={styles.chartsContainer}>
      <div className={styles.chartsColumnContainer}>
        <BillingTable summary={summary} />
        <ChartContainer style={{flex: 1, height: 400}}>
          <Summary
            summary={summary}
            title="Summary"
          />
        </ChartContainer>
      </div>
      <div className={styles.chartsColumnContainer}>
        <ChartContainer style={{height: 400}}>
          <GroupedBarChart
            data={resources && resources.loaded ? resources.value : {}}
            error={resources && resources.error ? resources.error : null}
            title="Resources"
            getBarAndNavigate={getBarAndNavigate}
          />
        </ChartContainer>
      </div>
    </div>
  );
}

function GroupReport ({
  group,
  billingCenters,
  billingCentersRequest,
  billingCentersTableRequest,
  resources,
  summary,
  getBarAndNavigate
}) {
  let title = 'User\'s spendings';
  let billingCenterName;
  if (billingCenters.loaded) {
    const [billingCenter] = (billingCenters.value || []).filter(c => +c.id === +group);
    if (billingCenter) {
      billingCenterName = billingCenter.name;
      title = `${billingCenter.name} user's spendings`;
    }
  }
  const tableColumns = [{
    key: 'user',
    dataIndex: 'name',
    title: 'User'
  }, {
    key: 'runs-duration',
    dataIndex: 'runsDuration',
    title: 'Runs duration (hours)',
    render: toValue
  }, {
    key: 'runs-count',
    dataIndex: 'runsCount',
    title: 'Runs count'
  }, {
    key: 'storage-usage',
    dataIndex: 'storageUsage',
    title: 'Storages usage (Gb)',
    render: toValue
  }, {
    key: 'spendings',
    dataIndex: 'spendings',
    title: 'Spendings',
    render: toMoneyValue
  }, {
    key: 'billingCenter',
    title: 'Billing center',
    render: () => billingCenterName
  }];
  return (
    <div className={styles.chartsContainer}>
      <div className={styles.chartsColumnContainer}>
        <ChartContainer>
          <BillingTable summary={summary} />
        </ChartContainer>
        <ChartContainer style={{height: 600}}>
          <Summary
            summary={summary}
            title="Summary"
          />
        </ChartContainer>
      </div>
      <div className={styles.chartsColumnContainer}>
        <ChartContainer style={{height: 400, position: 'relative'}}>
          <GroupedBarChart
            data={resources && resources.loaded ? resources.value : {}}
            error={resources && resources.error ? resources.error : null}
            title="Resources"
            getBarAndNavigate={getBarAndNavigate}
          />
        </ChartContainer>
        <ChartContainer style={{height: 400}}>
          <BarChart
            data={
              billingCentersRequest && billingCentersRequest.loaded
                ? billingCentersRequest.value
                : {}
            }
            error={billingCentersRequest && billingCentersRequest.error
              ? billingCentersRequest.error
              : null
            }
            title={title}
            getBarAndNavigate={getBarAndNavigate}
          />
        </ChartContainer>
        <Table
          dataSource={
            billingCentersTableRequest && billingCentersTableRequest.loaded
              ? Object.values(billingCentersTableRequest.value)
              : []
          }
          columns={tableColumns}
          loading={billingCentersTableRequest.pending}
          pagination={{
            current: billingCentersTableRequest.pageNum + 1,
            pageSize: billingCentersTableRequest.pageSize,
            total: billingCentersTableRequest.totalPages * billingCentersTableRequest.pageSize,
            onChange: async (page) => {
              await billingCentersTableRequest.fetchPage(page - 1);
            }
          }}
          size="small"
        />
      </div>
    </div>
  );
}

function GeneralReport ({
  billingCentersRequest,
  resources,
  summary,
  getBarAndNavigate
}) {
  return (
    <div className={styles.chartsContainer}>
      <div className={styles.chartsColumnContainer}>
        <ChartContainer>
          <BillingTable summary={summary} />
        </ChartContainer>
        <ChartContainer style={{height: 600}}>
          <Summary
            summary={summary}
            title="Summary"
          />
        </ChartContainer>
      </div>
      <div className={styles.chartsColumnContainer}>
        <ChartContainer style={{
          height: 400,
          position: 'relative',
          marginBottom: 40
        }}>
          <GroupedBarChart
            data={resources && resources.loaded ? resources.value : {}}
            error={resources && resources.error ? resources.error : null}
            getBarAndNavigate={getBarAndNavigate}
            title="Resources"
          />
        </ChartContainer>
        <ChartContainer style={{height: 400}}>
          <BarChart
            data={billingCentersRequest && billingCentersRequest.loaded
              ? billingCentersRequest.value
              : {}
            }
            error={billingCentersRequest && billingCentersRequest.error
              ? billingCentersRequest.error
              : null
            }
            title="Billing centers"
            getBarAndNavigate={getBarAndNavigate}
          />
        </ChartContainer>
      </div>
    </div>
  );
}

function DefaultReport (props) {
  const {user, group} = props;
  if (user) {
    return UserReport(props);
  }
  if (group) {
    return GroupReport(props);
  }
  return GeneralReport(props);
}

export default inject('billingCenters', 'users')(inject(injection)(observer(DefaultReport)));
