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
import {Pagination, Radio, Table} from 'antd';
import {
  BarChart,
  GroupedBarChart,
  BillingTable,
  PieChart,
  Summary
} from './charts';
import {Period, getPeriod} from './periods';
import Filters, {RUNNER_SEPARATOR, REGION_SEPARATOR} from './filters';
import {
  GetBillingData,
  GetGroupedBillingCenters,
  GetGroupedUsers,
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
import roleModel from '../../../utils/roleModel';
import styles from './reports.css';

function injection (stores, props) {
  const {location} = props;
  const {
    user: userQ,
    group: groupQ,
    period = Period.month,
    range,
    region: regionQ
  } = location.query;
  const group = groupQ ? groupQ.split(RUNNER_SEPARATOR) : undefined;
  const user = userQ ? userQ.split(RUNNER_SEPARATOR) : undefined;
  const cloudRegionId = regionQ && regionQ.length ? regionQ.split(REGION_SEPARATOR) : undefined;
  const periodInfo = getPeriod(period, range);
  const filters = {
    group,
    user,
    cloudRegionId,
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
  const exportComputeCsvRequest = new GetGroupedBillingCenters(
    {...periodInfo, resourceType: 'COMPUTE'},
    false
  );
  const exportStorageCsvRequest = new GetGroupedBillingCenters(
    {...periodInfo, resourceType: 'STORAGE'},
    false
  );
  const exportComputeByUsersCsvRequest = new GetGroupedUsers(
    {...periodInfo, resourceType: 'COMPUTE'},
    false
  );
  const exportStorageByUsersCsvRequest = new GetGroupedUsers(
    {...periodInfo, resourceType: 'STORAGE'},
    false
  );
  exportComputeCsvRequest.fetch();
  exportStorageCsvRequest.fetch();
  exportComputeByUsersCsvRequest.fetch();
  exportStorageByUsersCsvRequest.fetch();
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
    exportCsvRequest: [
      [exportComputeCsvRequest, exportStorageCsvRequest]
    ],
    exportByUsersCsvRequest: [
      [exportComputeByUsersCsvRequest, exportStorageByUsersCsvRequest]
    ]
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
          style={{display: 'flex', justifyContent: 'center', marginTop: 5}}
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
              style={height ? {height: height - 27} : {}}
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
              style={height ? {height: height - 27} : {}}
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
  exportCsvRequest,
  exportByUsersCsvRequest
}) {
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const composers = (discounts) => ([
    {
      composer: ExportComposers.billingCentersComposer,
      options: [exportCsvRequest, discounts]
    },
    {
      composer: ExportComposers.usersComposers,
      options: [exportByUsersCsvRequest, discounts]
    }
  ]);
  return (
    <Discounts.Consumer>
      {
        (computeDiscounts, storageDiscounts, computeDiscountValue, storageDiscountValue) => (
          <Export.Consumer
            className={styles.chartsContainer}
            composers={composers({
              compute: computeDiscounts,
              storage: storageDiscounts,
              computeValue: computeDiscountValue,
              storageValue: storageDiscountValue
            })}
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
    const pageSize = 6;
    const pending = requests.filter(r => r.pending).length > 0;
    const loaded = requests.filter(r => !r.loaded).length === 0;
    const rawData = loaded ? requests.map(r => r.value || {}) : {};
    const data = discounts.applyGroupedDataDiscounts(rawData, discountsFn);
    const tableData = loaded ? Object.values(data) : [];
    tableData.sort((a, b) => (b.spendings || 0) - (a.spendings || 0));
    const slicedData = tableData.slice((current - 1) * pageSize, current * pageSize);
    const paginationEnabled = tableData.length > pageSize;
    return (
      <div>
        <div
          style={{
            position: 'relative',
            overflow: 'auto',
            maxHeight: height - (paginationEnabled ? 30 : 0),
            padding: 5
          }}
        >
          <Table
            rowKey={(record) => `user-item_${record.name}`}
            rowClassName={() => styles.usersTableRow}
            dataSource={slicedData}
            columns={tableColumns}
            loading={pending}
            pagination={false}
            onRowClick={record => onUserSelect({key: record.name})}
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
                current={current}
                pageSize={pageSize}
                total={tableData.length}
                onChange={this.onChangePage}
                size="small"
              />
            </div>
          )
        }
      </div>
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
  authenticatedUserInfo,
  group,
  billingCentersComputeRequest,
  billingCentersStorageRequest,
  billingCentersComputeTableRequest,
  billingCentersStorageTableRequest,
  resources,
  summaryCompute,
  summaryStorages,
  filters,
  exportCsvRequest,
  exportByUsersCsvRequest
}) {
  const billingCenterName = (group || []).join(' ');
  const title = `${billingCenterName} user's spendings`;
  const tableColumns = [{
    key: 'user',
    dataIndex: 'name',
    title: 'User',
    render: user => (<DisplayUser userName={user} />),
    className: styles.tableCell
  }, {
    key: 'runs-duration',
    dataIndex: 'runsDuration',
    title: 'Runs duration (hours)',
    render: toValue,
    className: styles.tableCell
  }, {
    key: 'runs-count',
    dataIndex: 'runsCount',
    title: 'Runs count',
    className: styles.tableCell
  }, {
    key: 'storage-usage',
    dataIndex: 'storageUsage',
    title: 'Storages usage (Gb)',
    render: toValue,
    className: styles.tableCell
  }, {
    key: 'spendings',
    dataIndex: 'spendings',
    title: 'Spendings',
    render: costTickFormatter,
    className: styles.tableCell
  }, {
    key: 'billingCenter',
    title: 'Billing center',
    render: () => billingCenterName,
    className: styles.tableCell
  }];
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const props = {authenticatedUserInfo};
  const onUserSelect = roleModel.isManager.billing({props})
    ? navigation.wrapNavigation(navigation.usersNavigation, filters)
    : undefined;
  const composers = (discounts) => [
    {
      composer: ExportComposers.billingCentersComposer,
      options: [exportCsvRequest, discounts]
    },
    {
      composer: ExportComposers.usersComposers,
      options: [exportByUsersCsvRequest, discounts]
    }
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
  authenticatedUserInfo,
  billingCentersComputeRequest,
  billingCentersStorageRequest,
  resources,
  summaryCompute,
  summaryStorages,
  filters,
  exportCsvRequest,
  exportByUsersCsvRequest
}) {
  const onResourcesSelect = navigation.wrapNavigation(
    navigation.resourcesNavigation,
    filters
  );
  const props = {authenticatedUserInfo};
  const isBillingManager = roleModel.isManager.billing({props});
  const onBillingCenterSelect = isBillingManager
    ? navigation.wrapNavigation(navigation.billingCentersNavigation, filters)
    : undefined;
  const composers = (discounts) => [
    {
      composer: ExportComposers.billingCentersComposer,
      options: [exportCsvRequest, discounts]
    },
    {
      composer: ExportComposers.usersComposers,
      options: [exportByUsersCsvRequest, discounts]
    }
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
                {
                  isBillingManager && (
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
                  )
                }
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
      roleModel.authenticationInfo(
        observer(DefaultReport)
      )
    )
  )
);
