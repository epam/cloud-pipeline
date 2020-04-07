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
import {observer} from 'mobx-react';
import {observable} from 'mobx';
import {Button, Icon, message, Table} from 'antd';
import displayDate from '../../../utils/displayDate';
import SystemLogsFilter from '../../../models/system-logs/filter';
import styles from './logs.css';

const PAGE_SIZE = 20;
const PAGINATION_CONTROL_HEIGHT = 36;
const TABLE_HEADER_HEIGHT = 40;

const columns = [
  {
    key: 'date',
    dataIndex: 'timestamp',
    title: 'Date',
    render: d => displayDate(d),
    width: 150
  },
  {
    key: 'severity',
    dataIndex: 'severity',
    width: 50
  },
  {
    key: 'message',
    dataIndex: 'message',
    title: 'Log message'
  },
  {
    key: 'user',
    dataIndex: 'user',
    title: 'User',
    width: 100
  },
  {
    key: 'service',
    dataIndex: 'serviceName',
    title: 'Service',
    width: 200
  },
  {
    key: 'type',
    dataIndex: 'type',
    title: 'Type',
    width: 100
  },
];

@observer
class Logs extends React.Component {
  state = {
    pageIndex: 0,
    pages: [undefined]
  };

  @observable logs = new SystemLogsFilter();

  get logMessages () {
    if (this.logs.loaded) {
      return (this.logs.value.logEntries || []);
    }
    return [];
  }

  get currentPage () {
    const {pageIndex, pages} = this.state;
    if (pageIndex >= 0 && pageIndex < pages.length) {
      return pages[pageIndex];
    }
    return undefined;
  }

  get canNavigateToPreviousPage () {
    const {pageIndex} = this.state;
    return pageIndex > 0;
  }

  get canNavigateToNextPage () {
    const {pageIndex, pages} = this.state;
    return pages.length > pageIndex + 1;
  }

  navigateToPreviousPage = () => {
    const {pageIndex, pages} = this.state;
    this.setState({
      pages: pages.slice(0, pageIndex),
      pageIndex: pageIndex - 1
    }, this.onFiltersChanged);
  };

  navigateToNextPage = () => {
    const {pageIndex} = this.state;
    this.setState({
      pageIndex: pageIndex + 1
    }, this.onFiltersChanged);
  };

  get pages () {
    if (this.logs.loaded) {
      return this.logs.value.totalHits || 0;
    }
    return 0;
  }

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
    this.onFiltersChanged();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.filters !== this.props.filters) {
      this.clearPagination();
    }
  }

  clearPagination = () => {
    this.setState({pageIndex: 0, pages: [undefined]}, this.onFiltersChanged);
  };

  onFiltersChanged = () => {
    const {pages} = this.state;
    const {filters} = this.props;
    this.logs.send(
      {
        ...filters,
        pagination: {
          token: this.currentPage,
          pageSize: PAGE_SIZE
        }
      }
    )
      .then(() => {
        if (this.logs.error) {
          message.error(this.logs.error, 5);
        } else {
          const {token} = this.logs.value;
          if (token) {
            pages.push(token);
          }
          this.setState({pages});
        }
      })
      .catch(error => {
        message.error(error.toString(), 5);
      });
  };

  render () {
    const {height} = this.props;
    if (!height) {
      return null;
    }
    return (
      <div style={{height, width: '100%'}}>
        <Table
          style={{height: height - PAGINATION_CONTROL_HEIGHT}}
          columns={columns}
          dataSource={this.logMessages}
          loading={this.logs.pending}
          size="small"
          scroll={{y: height - PAGINATION_CONTROL_HEIGHT - TABLE_HEADER_HEIGHT}}
          pagination={false}
        />
        <div
          style={{
            height: PAGINATION_CONTROL_HEIGHT,
            lineHeight: `${PAGINATION_CONTROL_HEIGHT}px`
          }}
          className={styles.pagination}
        >
          <Button
            className={styles.button}
            size="small"
            disabled={!this.canNavigateToPreviousPage}
            onClick={this.navigateToPreviousPage}
          >
            <Icon type="caret-left" />
          </Button>
          <Button
            className={styles.button}
            size="small"
            disabled={!this.canNavigateToNextPage}
            onClick={this.navigateToNextPage}
          >
            <Icon type="caret-right" />
          </Button>
        </div>
      </div>
    );
  }
}

Logs.propTypes = {
  filters: PropTypes.object,
  height: PropTypes.number,
  onInitialized: PropTypes.func
};

export default Logs;
