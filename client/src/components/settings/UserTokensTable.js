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
import {Button, message, Table} from 'antd';
import {computed} from 'mobx';
import {observer} from 'mobx-react';
import displayDate from '../../utils/displayDate';
import roleModel from '../../utils/roleModel';
import UserNamedTokens from '../../models/user/UserNamedTokens';
import UserNamedTokenRevoke from '../../models/user/UserNamedTokenRevoke';
import styles from './UserTokensTable.css';

@roleModel.authenticationInfo
@observer
export default class UserTokensTable extends React.Component {
  static propTypes = {
    userId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    refreshToken: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
  };

  state={
    pending: false,
    userTokens: [],
    revokingJti: null
  };

  columns = [
    {
      title: 'ID',
      dataIndex: 'jti',
      key: 'jti',
      className: styles.idColumn,
      render: (jti) => jti
    },
    {
      title: 'Name',
      dataIndex: 'tokenName',
      key: 'tokenName',
      className: styles.nameColumn,
      render: (tokenName) => (tokenName || '—')
    },
    {
      title: 'Expiration date',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      className: styles.expiresColumn,
      render: (expiresAt) => (expiresAt ? displayDate(expiresAt) : '—')
    },
    {
      title: '',
      key: 'revoke',
      className: styles.revokeColumn,
      render: (_, record) => (
        <Button
          id={`revoke-user-token-${record.jti}`}
          loading={this.state.revokingJti === record.jti}
          onClick={() => this.revokeToken(record)}
          size="small"
          type="danger"
          disabled={!!this.state.revokingJti}
        >
          Revoke
        </Button>
      )
    }
  ];

  componentDidMount () {
    this.fetchData();
  }

  componentDidUpdate (prevProps) {
    const prevId = prevProps.userId;
    const currentId = this.props.userId;
    if (
      prevId !== currentId ||
      prevProps.refreshToken !== this.props.refreshToken
    ) {
      this.fetchData();
    }
  }

  @computed
  get currentUserId () {
    if (this.props.authenticatedUserInfo && this.props.authenticatedUserInfo.loaded) {
      const user = this.props.authenticatedUserInfo.value;
      return user.id;
    }
    return undefined;
  }

  @computed
  get targetUserId () {
    const {userId} = this.props;
    if (userId !== null && userId !== undefined) {
      return Number(userId);
    }
    return this.currentUserId;
  }

  fetchData = async () => {
    if (!this.targetUserId) {
      return this.setState({
        pending: false,
        userTokens: []
      });
    }
    this.setState({pending: true});
    const id = this.targetUserId === this.currentUserId ? undefined : this.targetUserId;
    const userNamedTokens = new UserNamedTokens(id);
    await userNamedTokens.fetch();
    this.setState({
      pending: false,
      userTokens: userNamedTokens.loaded
        ? (userNamedTokens.value || []).map(v => v)
        : []
    });
  };

  revokeToken = async (record) => {
    const jti = record?.jti;
    if (!jti) {
      return;
    }
    this.setState({revokingJti: jti});
    try {
      const revoke = new UserNamedTokenRevoke(jti, this.targetUserId);
      await revoke.fetch();
      if (revoke.loaded && revoke.value) {
        const msg = record.tokenName
          ? `Token ${record.tokenName} revoked.`
          : 'Token revoked.';
        message.success(msg);
        await this.fetchData();
      } else {
        message.error(revoke.error || 'Failed to revoke token', 5);
      }
    } finally {
      this.setState({revokingJti: null});
    }
  };

  render () {
    return (
      <Table
        className={styles.table}
        columns={this.columns}
        dataSource={this.state.userTokens}
        pagination={false}
        rowKey="jti"
        size="small"
        loading={this.state.pending}
      />
    );
  }
}
