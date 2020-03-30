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
import {message, Table} from 'antd';
import displayDate from '../../../utils/displayDate';
import SystemLogsFilter from '../../../models/system-logs/filter';

const PAGE_SIZE = 20;

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
  {
    key: 'source',
    dataIndex: 'source',
    title: 'Source',
    width: 300
  }
];

@observer
class Logs extends React.Component {
  state = {
    logs: [],
    page: 0
  };

  @observable logs = new SystemLogsFilter();

  get logMessages () {
    if (this.logs.loaded) {
      return (this.logs.value || []);
    }
    return [];
  }

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
    this.onFiltersChanged(0);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.filters !== this.props.filters) {
      this.onFiltersChanged(0);
    }
  }

  onFiltersChanged = (page) => {
    const {filters} = this.props;
    this.logs.send(
      {
        ...filters,
        pagination: {
          token: page,
          pageSize: PAGE_SIZE
        }
      }
    )
      .then(() => {
        if (this.logs.error) {
          message.error(this.logs.error, 5);
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
          style={{height}}
          columns={columns}
          dataSource={this.logMessages}
          loading={this.logs.pending}
          pagination={false}
          size="small"
          scroll={{y: height - 75}}
        />
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
