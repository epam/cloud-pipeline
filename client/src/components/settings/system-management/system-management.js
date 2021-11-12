/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Table} from 'antd';
import classNames from 'classnames';
import {SplitPanel} from '../../special/splitPanel';
import SystemLogs from './system-logs';
import NATGetaway from './nat-getaway-configuration/nat-getaway-configuration';
import styles from './system-management.css';

const SYSTEM_LOGS_TITLE = 'LOGS';
const NAT_GATEWAY_TITLE = 'NAT GETAWAY';

export default class SystemManagement extends React.Component {
  state = {
    contentToShow: this.systemManagementItems[0].title
  }
  get systemManagementItems () {
    return [
      {title: SYSTEM_LOGS_TITLE},
      {title: NAT_GATEWAY_TITLE}
    ];
  }

  showSelectedItem = (title) => {
    this.setState({
      contentToShow: title
    });
  }

  renderSidebarList = () => {
    const columns = [{key: 'title', dataIndex: 'title', render: (title) => title}];
    return (
      <Table
        rowKey="title"
        showHeader={false}
        pagination={false}
        dataSource={this.systemManagementItems}
        columns={columns}
        rowClassName={
          (item) => classNames(
            styles.itemRow,
            {'cp-table-element-selected': item.title === this.state.contentToShow}
          )
        }
        onRowClick={(item) => this.showSelectedItem(item.title)} />
    );
  }
  render () {
    const {contentToShow} = this.state;
    return (
      <SplitPanel
        contentInfo={[
          {
            key: 'list',
            size: {
              pxDefault: 200
            }
          }
        ]}>
        <div key="list">{this.renderSidebarList()}</div>
        <div>
          {contentToShow === SYSTEM_LOGS_TITLE && <SystemLogs />}
          {contentToShow === NAT_GATEWAY_TITLE && <NATGetaway />}
        </div>
      </SplitPanel>
    );
  }
}
