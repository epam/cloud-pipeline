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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Radio, Table} from 'antd';
import {
  BarChart,
  GroupedBarChart,
  BillingTable,
  PieChart,
  Summary
} from './charts';
import {Period, getPeriod} from './periods';
import Filters, {RUNNER_SEPARATOR} from './filters';
import {
  GetBillingData,
  GetGroupedBillingCenters,
  GetGroupedBillingCentersWithPrevious,
  GetGroupedResourcesWithPrevious
} from '../../../models/billing';
import * as navigation from './navigation';
import Discounts, {discounts} from './discounts';
import Export, {ExportComposers} from './export';
import {
  costTickFormatter,
  getUserDisplayInfo,
  DisplayUser,
  ResizableContainer
} from './utilities';
import {GeneralReportLayout, Layout} from './layout';
import styles from './reports.css';

function injection (stores, props) {
  const {location} = props;
  const {
    user: userQ,
    group: groupQ,
    period = Period.month,
    range
  } = location.query;
  const group = groupQ ? groupQ.split(RUNNER_SEPARATOR) : undefined;
  const user = userQ ? userQ.split(RUNNER_SEPARATOR) : undefined;
  const periodInfo = getPeriod(period, range);
  const prevPeriodInfo = getPeriod(period, periodInfo.before);
  const prevPrevPeriodInfo = getPeriod(period, prevPeriodInfo.before);
  const filters = {
    group,
    user,
    ...periodInfo
  };
  const billingCentersStorageRequest = new GetGroupedBillingCentersWithPrevious(
    {...filters, resourceType: 'STORAGE'}
  );
  billingCentersStorageRequest.fetch();
  const billingCentersComputeRequest = new GetGroupedBillingCentersWithPrevious(
    {...filters, resourceType: 'COMPUTE'}
  );
  billingCentersComputeRequest.fetch();
  let billingCentersComputeTableRequest;
  let billingCentersStorageTableRequest;
  if (group) {
    billingCentersComputeTableRequest = new GetGroupedBillingCenters(
      {...filters, resourceType: 'COMPUTE'}
    );
    billingCentersComputeTableRequest.fetch();
    billingCentersStorageTableRequest = new GetGroupedBillingCenters(
      {...filters, resourceType: 'STORAGE'}
    );
    billingCentersStorageTableRequest.fetch();
  }
  const export1ComputeCsvRequest = new GetGroupedBillingCenters(
    {...periodInfo, resourceType: 'COMPUTE'},
    false
  );
  const export2ComputeCsvRequest = new GetGroupedBillingCenters(
    {...prevPeriodInfo, resourceType: 'COMPUTE'},
    false
  );
  const export3ComputeCsvRequest = new GetGroupedBillingCenters(
    {...prevPrevPeriodInfo, resourceType: 'COMPUTE'},
    false
  );
  const export1StorageCsvRequest = new GetGroupedBillingCenters(
    {...periodInfo, resourceType: 'STORAGE'},
    false
  );
  const export2StorageCsvRequest = new GetGroupedBillingCenters(
    {...prevPeriodInfo, resourceType: 'STORAGE'},
    false
  );
  const export3StorageCsvRequest = new GetGroupedBillingCenters(
    {...prevPrevPeriodInfo, resourceType: 'STORAGE'},
    false
  );
  export1ComputeCsvRequest.fetch();
  export2ComputeCsvRequest.fetch();
  export3ComputeCsvRequest.fetch();
  export1StorageCsvRequest.fetch();
  export2StorageCsvRequest.fetch();
  export3StorageCsvRequest.fetch();
  const resources = new GetGroupedResourcesWithPrevious(filters);
  resources.fetch();
  const summaryCompute = new GetBillingData(
    {
      ...filters,
      filterBy: GetBillingData.FILTER_BY.compute
    }
  );
  summaryCompute.fetch();
  const summaryStorages = new GetBillingData(
    {
      ...filters,
      filterBy: GetBillingData.FILTER_BY.storages
    }
  );
  summaryStorages.fetch();
  return {
    user,
    group,
    summaryCompute,
    summaryStorages,
    billingCentersComputeRequest,
    billingCentersStorageRequest,
    billingCentersComputeTableRequest,
    billingCentersStorageTableRequest,
    resources,
    export1ComputeCsvRequest,
    export1StorageCsvRequest,
    exportCsvRequest: [
      period === Period.custom
        ? undefined
        : [export3ComputeCsvRequest, export3StorageCsvRequest],
      period === Period.custom
        ? undefined
        : [export2ComputeCsvRequest, export2StorageCsvRequest],
      [export1ComputeCsvRequest, export1StorageCsvRequest]
    ].filter(Boolean)
  };
}

function toValue (value) {
  if (value) {
    return Math.round((+value) * 100.0) / 100.0;
  }
  return null;
}

const BillingCentersDisplayModes = {
  bar: 'bar',
  pie: 'chart'
};

class BillingCenters extends React.Component {
  state = {
    mode: BillingCentersDisplayModes.bar
  };

  onChangeMode = (e) => {
    this.setState({mode: e.target.value});
  };

  render () {
    const {
      request,
      discounts: discountsFn,
      onSelect,
      height,
      title
    } = this.props;
    const {mode} = this.state;
    return (
      <div>
        <Radio.Group
          value={mode}
          onChange={this.onChangeMode}
          style={{display: 'flex', justifyContent: 'center'}}
          size="small"
        >
          <Radio.Button
            key={BillingCentersDisplayModes.bar}
            value={BillingCentersDisplayModes.bar}
          >
            Bar chart
          </Radio.Button>
          <Radio.Button
            key={BillingCentersDisplayModes.pie}
            value={BillingCentersDisplayModes.pie}
          >
            Pie chart
          </Radio.Button>
        </Radio.Group>
        {
          mode === BillingCentersDisplayModes.bar && (
            <BarChart
              request={request}
              discounts={discountsFn}
              title={title}
              onSelect={onSelect}
              style={height ? {height: height - 22} : {}}
            />
          )
        }
        {
          mode === BillingCentersDisplayModes.pie && (
            <PieChart
              request={request}
              discounts={discountsFn}
              title={title}
              onSelect={onSelect}
              style={height ? {height: height - 22} : {}}
            />
          )
        }
      </div>
    );
  }
}

BillingCenters.propTypes = {
  request: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  discounts: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  onSelect: PropTypes.func,
  title: PropTypes.node,
  height: PropTypes.number
};

function UserReport ({
  resources,
  summaryCompute,
  summaryStorages,
  filters,
  exportCsvRequest
}) {
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const composers = (discounts) => [
    {
      composer: ExportComposers.billingCentersComposer,
      options: [exportCsvRequest, discounts]
    }
    // {
    //   composer: ExportComposers.summaryComposer,
    //   options: [summary]
    // },
    // {
    //   composer: ExportComposers.resourcesComposer,
    //   options: [resources]
    // }
  ];
  return (
    <Discounts.Consumer>
      {
        (computeDiscounts, storageDiscounts, computeDiscountValue, storageDiscountValue) => (
          <Export.Consumer
            className={styles.chartsContainer}
            composers={
              composers({
                compute: computeDiscounts,
                storage: storageDiscounts,
                computeValue: computeDiscountValue,
                storageValue: storageDiscountValue
              })
            }
          >
            <Layout
              layout={GeneralReportLayout.Layout}
              gridStyles={GeneralReportLayout.GridStyles}
              staticPanels={[GeneralReportLayout.Panels.runners]}
            >
              <div key={GeneralReportLayout.Panels.summary}>
                <Layout.Panel
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    minHeight: 0
                  }}
                >
                  <BillingTable
                    compute={summaryCompute}
                    storages={summaryStorages}
                    computeDiscounts={computeDiscounts}
                    storagesDiscounts={storageDiscounts}
                  />
                  <ResizableContainer style={{flex: 1}}>
                    {
                      ({width, height}) => (
                        <Summary
                          compute={summaryCompute}
                          storages={summaryStorages}
                          computeDiscounts={computeDiscounts}
                          storagesDiscounts={storageDiscounts}
                          title="Summary"
                          style={{width, height}}
                        />
                      )
                    }
                  </ResizableContainer>
                </Layout.Panel>
              </div>
              <div key={GeneralReportLayout.Panels.resources}>
                <Layout.Panel>
                  <ResizableContainer style={{width: '100%', height: '100%'}}>
                    {
                      ({height}) => (
                        <GroupedBarChart
                          request={resources}
                          discountsMapper={{
                            'Storage': storageDiscounts,
                            'Compute instances': computeDiscounts
                          }}
                          title="Resources"
                          onSelect={onResourcesSelect}
                          height={height}
                        />
                      )
                    }
                  </ResizableContainer>
                </Layout.Panel>
              </div>
              <div key={GeneralReportLayout.Panels.runners}>
                {'\u00A0'}
              </div>
            </Layout>
          </Export.Consumer>
        )
      }
    </Discounts.Consumer>
  );
}

@observer
class BillingCentersTable extends React.Component {
  state = {
    current: 1
  };

  onChangePage = (page) => {
    this.setState({current: page});
  }

  render () {
    const {
      columns: tableColumns,
      requests,
      discounts: discountsFn,
      onUserSelect,
      height
    } = this.props;
    const {current} = this.state;
    const pageSize = 2;
    const pending = requests.filter(r => r.pending).length > 0;
    const loaded = requests.filter(r => !r.loaded).length === 0;
    const rawData = loaded ? requests.map(r => r.value || {}) : {};
    const data = discounts.applyGroupedDataDiscounts(rawData, discountsFn);
    const tableData = loaded ? Object.values(data) : [];
    return (
      <Table
        rowKey={(record) => `user-item_${record.name}`}
        rowClassName={() => styles.usersTableRow}
        dataSource={tableData.slice((current - 1) * pageSize, current * pageSize)}
        columns={tableColumns}
        loading={pending}
        pagination={{
          current,
          pageSize,
          total: tableData.length,
          onChange: this.onChangePage
        }}
        onRowClick={record => onUserSelect({key: record.name})}
        size="small"
        scroll={{y: height - 100}}
      />
    );
  }
}

BillingCentersTable.propTypes = {
  requests: PropTypes.array,
  discounts: PropTypes.array,
  onUserSelect: PropTypes.func,
  columns: PropTypes.array,
  height: PropTypes.number
};

class UsersChartComponent extends React.Component {
  componentDidMount () {
    const {users, preferences} = this.props;
    (async () => {
      await users.fetchIfNeededOrWait();
      await preferences.fetchIfNeededOrWait();
    })();
  }

  render () {
    const {
      request,
      discounts,
      title,
      onSelect,
      style,
      users,
      preferences
    } = this.props;
    return (
      <BarChart
        request={request}
        discounts={discounts}
        title={title}
        onSelect={onSelect}
        style={style}
        itemNameFn={name => getUserDisplayInfo(name, users, preferences)}
      />
    );
  }
}

const UsersChart = inject('users', 'preferences')(observer(UsersChartComponent));

function GroupReport ({
  group,
  billingCentersComputeRequest,
  billingCentersStorageRequest,
  billingCentersComputeTableRequest,
  billingCentersStorageTableRequest,
  resources,
  summaryCompute,
  summaryStorages,
  filters,
  exportCsvRequest
}) {
  const billingCenterName = (group || []).join(' ');
  const title = `${billingCenterName} user's spendings`;
  const tableColumns = [{
    key: 'user',
    dataIndex: 'name',
    title: 'User',
    render: user => (<DisplayUser userName={user} />)
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
    filters
  );
  const composers = (discounts) => [
    {
      composer: ExportComposers.billingCentersComposer,
      options: [exportCsvRequest, discounts]
    }
    // {
    //   composer: ExportComposers.summaryComposer,
    //   options: [summary]
    // },
    // {
    //   composer: ExportComposers.resourcesComposer,
    //   options: [resources]
    // },
    // {
    //   composer: ExportComposers.defaultComposer,
    //   options: [
    //     billingCentersRequest,
    //     {
    //       runs_duration: 'runsDuration',
    //       runs_count: 'runsCount',
    //       billing_center: () => group
    //     }
    //   ]
    // }
  ];
  return (
    <Discounts.Consumer>
      {
        (computeDiscounts, storageDiscounts, computeDiscountValue, storageDiscountValue) => (
          <Export.Consumer
            className={styles.chartsContainer}
            composers={
              composers({
                compute: computeDiscounts,
                storage: storageDiscounts,
                computeValue: computeDiscountValue,
                storageValue: storageDiscountValue
              })
            }
          >
            <Layout
              layout={GeneralReportLayout.Layout}
              gridStyles={GeneralReportLayout.GridStyles}
            >
              <div key={GeneralReportLayout.Panels.summary}>
                <Layout.Panel
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    minHeight: 0
                  }}
                >
                  <BillingTable
                    compute={summaryCompute}
                    storages={summaryStorages}
                    computeDiscounts={computeDiscounts}
                    storagesDiscounts={storageDiscounts}
                  />
                  <ResizableContainer style={{flex: 1}}>
                    {
                      ({width, height}) => (
                        <Summary
                          compute={summaryCompute}
                          storages={summaryStorages}
                          computeDiscounts={computeDiscounts}
                          storagesDiscounts={storageDiscounts}
                          title="Summary"
                          style={{width, height}}
                        />
                      )
                    }
                  </ResizableContainer>
                </Layout.Panel>
              </div>
              <div key={GeneralReportLayout.Panels.resources}>
                <Layout.Panel>
                  <ResizableContainer style={{width: '100%', height: '100%'}}>
                    {
                      ({height}) => (
                        <GroupedBarChart
                          request={resources}
                          discountsMapper={{
                            'Storage': storageDiscounts,
                            'Compute instances': computeDiscounts
                          }}
                          title="Resources"
                          onSelect={onResourcesSelect}
                          height={height}
                        />
                      )
                    }
                  </ResizableContainer>
                </Layout.Panel>
              </div>
              <div key={GeneralReportLayout.Panels.runners}>
                <Layout.Panel>
                  <ResizableContainer style={{width: '100%', height: '100%'}}>
                    {
                      ({height}) => (
                        <div>
                          <UsersChart
                            request={[billingCentersComputeRequest, billingCentersStorageRequest]}
                            discounts={[computeDiscounts, storageDiscounts]}
                            title={title}
                            onSelect={onUserSelect}
                            style={{height: height / 2.0}}
                          />
                          <BillingCentersTable
                            requests={[
                              billingCentersComputeTableRequest,
                              billingCentersStorageTableRequest
                            ]}
                            discounts={[computeDiscounts, storageDiscounts]}
                            columns={tableColumns}
                            onUserSelect={onUserSelect}
                            height={height / 2.0}
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

function GeneralReport ({
  billingCentersComputeRequest,
  billingCentersStorageRequest,
  resources,
  summaryCompute,
  summaryStorages,
  filters,
  exportCsvRequest
}) {
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const onBillingCenterSelect = navigation.wrapNavigation(
    navigation.billingCentersNavigation,
    filters
  );
  const composers = (discounts) => [
    {
      composer: ExportComposers.billingCentersComposer,
      options: [exportCsvRequest, discounts]
    }
    // {
    //   composer: ExportComposers.summaryComposer,
    //   options: [summary]
    // },
    // {
    //   composer: ExportComposers.resourcesComposer,
    //   options: [resources]
    // },
    // {
    //   composer: ExportComposers.defaultComposer,
    //   options: [billingCentersRequest]
    // }
  ];
  return (
    <Discounts.Consumer>
      {
        (computeDiscounts, storageDiscounts, computeDiscountValue, storageDiscountValue) => (
          <Export.Consumer
            className={styles.chartsContainer}
            composers={
              composers({
                compute: computeDiscounts,
                storage: storageDiscounts,
                computeValue: computeDiscountValue,
                storageValue: storageDiscountValue
              })
            }
          >
            <Layout
              layout={GeneralReportLayout.Layout}
              gridStyles={GeneralReportLayout.GridStyles}
            >
              <div
                key={GeneralReportLayout.Panels.summary}
              >
                <Layout.Panel
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    minHeight: 0
                  }}
                >
                  <BillingTable
                    compute={summaryCompute}
                    storages={summaryStorages}
                    computeDiscounts={computeDiscounts}
                    storagesDiscounts={storageDiscounts}
                  />
                  <ResizableContainer style={{flex: 1}}>
                    {
                      ({width, height}) => (
                        <Summary
                          compute={summaryCompute}
                          storages={summaryStorages}
                          computeDiscounts={computeDiscounts}
                          storagesDiscounts={storageDiscounts}
                          title="Summary"
                          style={{width, height}}
                        />
                      )
                    }
                  </ResizableContainer>
                </Layout.Panel>
              </div>
              <div
                key={GeneralReportLayout.Panels.resources}
              >
                <Layout.Panel>
                  <ResizableContainer style={{width: '100%', height: '100%'}}>
                    {
                      ({height}) => (
                        <GroupedBarChart
                          request={resources}
                          discountsMapper={{
                            'Storage': storageDiscounts,
                            'Compute instances': computeDiscounts
                          }}
                          onSelect={onResourcesSelect}
                          title="Resources"
                          height={height}
                        />
                      )
                    }
                  </ResizableContainer>
                </Layout.Panel>
              </div>
              <div
                key={GeneralReportLayout.Panels.runners}
              >
                <Layout.Panel>
                  <ResizableContainer style={{width: '100%', height: '100%'}}>
                    {
                      ({height}) => (
                        <BillingCenters
                          request={[billingCentersComputeRequest, billingCentersStorageRequest]}
                          discounts={[computeDiscounts, storageDiscounts]}
                          onSelect={onBillingCenterSelect}
                          title="Billing centers"
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

function DefaultReport (props) {
  const {user, group} = props;
  if (user) {
    return UserReport(props);
  }
  if (group && group.length === 1) {
    return GroupReport(props);
  }
  return GeneralReport(props);
}

export default inject('billingCenters')(
  inject(injection)(
    Filters.attach(
      observer(DefaultReport)
    )
  )
);
