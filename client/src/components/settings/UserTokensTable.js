/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Table} from 'antd';
import moment from 'moment-timezone';
import displayDate from '../../utils/displayDate';

/**
 * Placeholder data until the API exposes token listing.
 */
const MOCK_USER_TOKENS = [
  {
    id: '7a2f9c01',
    name: 'Test name 1',
    expiresAt: moment().add(12, 'days').endOf('day')
  },
  {
    id: '3e8b1d44',
    name: 'Test name 2',
    expiresAt: moment().add(45, 'days').endOf('day')
  },
  {
    id: '9c0e55aa',
    name: '',
    expiresAt: moment().add(6, 'months').endOf('day')
  }
];

export default class UserTokensTable extends React.Component {
  static propTypes = {
    user: PropTypes.object,
    refreshToken: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
  };

  state={
    pending: false
  };

  columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id'
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name) => (name || '—')
    },
    {
      title: 'Expiration date',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      render: (expiresAt) => (expiresAt ? displayDate(expiresAt) : '—')
    },
    {
      title: '',
      key: 'revoke',
      width: 80,
      onCell: () => ({style: {whiteSpace: 'nowrap', width: 1}}),
      onHeaderCell: () => ({style: {width: 1}}),
      render: (_, record) => (
        <Button
          id={`revoke-user-token-${record.id}`}
          onClick={(e) => {}}
          size="small"
          type="danger">
          Revoke
        </Button>
      )
    }
  ];

  componentDidMount () {
    this.fetchData();
  }

  componentDidUpdate (prevProps) {
    const prevId = prevProps.user ? prevProps.user.id : undefined;
    const currentId = this.props.user ? this.props.user.id : undefined;
    if (
      prevId !== currentId ||
      prevProps.refreshToken !== this.props.refreshToken
    ) {
      this.fetchData();
    }
  }

  fetchData = async () => {
    const sleepMock = (timeout) => new Promise(resolve => setTimeout(() => resolve(), timeout));
    this.setState({pending: true});
    await sleepMock(1000);
    this.setState({pending: false});
  };

  render () {
    return (
      <Table
        className="user-tokens-table"
        columns={this.columns}
        dataSource={MOCK_USER_TOKENS}
        pagination={false}
        rowKey="id"
        size="small"
        style={{marginTop: 16}}
        loading={this.state.pending}
      />
    );
  }
}
