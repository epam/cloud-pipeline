import React from 'react';
import {Table} from 'antd';

import {SplitPanel} from '../../special/splitPanel/SplitPanel';
import SystemLogs from './system-logs';
import NATGetaway from './nat-getaway-configuration/nat-getaway-configuration';
import styles from './system-management.css';

const SYSTEM_LOGS_TITLE = 'LOGS';
const NAT_GATEAWAY_TITLE = 'NAT GETAWAY';

export default class SystemManagement extends React.Component {

  state = {
    contentToShow: this.systemManagementItems[0].title
  }
  get systemManagementItems () {
    return [
      {title: SYSTEM_LOGS_TITLE},
      {title: NAT_GATEAWAY_TITLE}
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
          (item) => item.title === this.state.contentToShow
            ? `${styles.itemRow} ${styles.selected}`
            : styles.itemRow
        }
        onRowClick={(item) => this.showSelectedItem(item.title)} />
    );
  }
  render () {
    const {contentToShow} = this.state;
    return (
      <SplitPanel>
        <div className={styles.leftSidebar}>{this.renderSidebarList()}</div>
        <div className={styles.contentPanel}>
          {contentToShow === SYSTEM_LOGS_TITLE && <SystemLogs />}
          {contentToShow === NAT_GATEAWAY_TITLE && <NATGetaway />}
        </div>
      </SplitPanel>
    );
  }
}
