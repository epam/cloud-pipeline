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
import Filters from './filters';
import {
  GetBillingData,
  GetGroupedBillingCenters,
  GetGroupedBillingCentersWithPrevious,
  GetGroupedResourcesWithPrevious
} from '../../../models/billing';
import * as navigation from './navigation';
import Export, {ExportComposers} from './export';
import {costTickFormatter} from './utilities';
import styles from './reports.css';

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
  const billingCentersRequest = new GetGroupedBillingCentersWithPrevious(filters, true);
  billingCentersRequest.fetch();
  let billingCentersTableRequest;
  if (group) {
    billingCentersTableRequest = new GetGroupedBillingCenters(filters, true);
    billingCentersTableRequest.fetch();
  }
  const resources = new GetGroupedResourcesWithPrevious(filters);
  resources.fetch();
  const summary = new GetBillingData(filters);
  summary.fetch();
  return {
    user,
    group,
    summary,
    billingCentersRequest,
    billingCentersTableRequest,
    resources
  };
}

function toValue (value) {
  if (value) {
    return Math.round((+value) * 100.0) / 100.0;
  }
  return null;
}

function GeneralDataBlock ({children, style}) {
  return (
    <div className={styles.generalChartsContainer}>
      <div style={style}>
        {children}
      </div>
    </div>
  );
}

function UserReport ({
  resources,
  summary,
  filters
}) {
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const composers = [
    {
      composer: ExportComposers.summaryComposer,
      options: [summary]
    },
    {
      composer: ExportComposers.resourcesComposer,
      options: [resources]
    }
  ];
  return (
    <Export.Consumer
      className={styles.chartsContainer}
      composers={composers}
    >
      <GeneralDataBlock>
        <BillingTable summary={summary} />
        <Summary
          summary={summary}
          title="Summary"
          style={{flex: 1, maxHeight: 500}}
        />
      </GeneralDataBlock>
      <GeneralDataBlock>
        <GroupedBarChart
          request={resources}
          title="Resources"
          onSelect={onResourcesSelect}
          height={400}
        />
      </GeneralDataBlock>
    </Export.Consumer>
  );
}

function GroupReport ({
  group,
  billingCentersRequest,
  billingCentersTableRequest,
  resources,
  summary,
  filters,
  users
}) {
  const billingCenterName = group;
  const title = `${billingCenterName} user's spendings`;
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
    render: costTickFormatter
  }, {
    key: 'billingCenter',
    title: 'Billing center',
    render: () => billingCenterName
  }];
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const onUserSelect = navigation.wrapNavigation(
    navigation.usersNavigation,
    filters,
    users
  );
  const composers = [
    {
      composer: ExportComposers.summaryComposer,
      options: [summary]
    },
    {
      composer: ExportComposers.resourcesComposer,
      options: [resources]
    },
    {
      composer: ExportComposers.defaultComposer,
      options: [
        billingCentersRequest,
        {
          runs_duration: 'runsDuration',
          runs_count: 'runsCount',
          billing_center: () => group
        }
      ]
    }
  ];
  return (
    <Export.Consumer
      className={styles.chartsContainer}
      composers={composers}
    >
      <GeneralDataBlock>
        <BillingTable summary={summary} />
        <Summary
          summary={summary}
          title="Summary"
          style={{flex: 1, maxHeight: 500}}
        />
      </GeneralDataBlock>
      <div className={styles.chartsSubContainer}>
        <GeneralDataBlock>
          <GroupedBarChart
            request={resources}
            title="Resources"
            onSelect={onResourcesSelect}
            height={400}
          />
        </GeneralDataBlock>
        <GeneralDataBlock>
          <BarChart
            request={billingCentersRequest}
            title={title}
            onSelect={onUserSelect}
            style={{height: 250}}
          />
          <Table
            rowKey={(record) => `user-item_${record.name}`}
            rowClassName={() => styles.usersTableRow}
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
            onRowClick={record => onUserSelect({key: record.name})}
            size="small"
          />
        </GeneralDataBlock>
      </div>
    </Export.Consumer>
  );
}

function GeneralReport ({
  billingCentersRequest,
  resources,
  summary,
  filters
}) {
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const onBillingCenterSelect = navigation.wrapNavigation(
    navigation.billingCentersNavigation,
    filters
  );
  const composers = [
    {
      composer: ExportComposers.summaryComposer,
      options: [summary]
    },
    {
      composer: ExportComposers.resourcesComposer,
      options: [resources]
    },
    {
      composer: ExportComposers.defaultComposer,
      options: [billingCentersRequest]
    }
  ];
  return (
    <Export.Consumer
      className={styles.chartsContainer}
      composers={composers}
    >
      <GeneralDataBlock>
        <BillingTable summary={summary} />
        <Summary
          summary={summary}
          title="Summary"
          style={{flex: 1, maxHeight: 500}}
        />
      </GeneralDataBlock>
      <div className={styles.chartsSubContainer}>
        <GeneralDataBlock style={{
          position: 'relative',
          flex: 'unset'
        }}>
          <GroupedBarChart
            request={resources}
            onSelect={onResourcesSelect}
            title="Resources"
            height={400}
          />
        </GeneralDataBlock>
        <GeneralDataBlock style={{flex: 'unset'}}>
          <BarChart
            request={billingCentersRequest}
            title="Billing centers"
            onSelect={onBillingCenterSelect}
            style={{height: 400}}
          />
        </GeneralDataBlock>
      </div>
    </Export.Consumer>
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

export default inject('billingCenters', 'users')(
  inject(injection)(
    Filters.attach(
      observer(DefaultReport)
    )
  )
);
