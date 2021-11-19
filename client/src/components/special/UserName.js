/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {UserOutlined} from '@ant-design/icons';
import {Row, Tooltip} from 'antd';

@inject('usersInfo')
@observer
export default class UserName extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    userName: PropTypes.string,
    style: PropTypes.object,
    showIcon: PropTypes.bool
  };

  @computed
  get user () {
    if (this.props.usersInfo.loaded && this.props.userName) {
      const [user] = (this.props.usersInfo.value || [])
        .filter(u => u.name.toLowerCase() === this.props.userName.toLowerCase());
      return user;
    }
    return null;
  }

  renderUserAttributes = (user) => {
    if (user.attributes) {
      const getAttributesValues = () => {
        const values = [];
        for (let key in user.attributes) {
          if (user.attributes.hasOwnProperty(key)) {
            values.push(user.attributes[key]);
          }
        }
        return values;
      };
      const attributesString = getAttributesValues().join(', ');
      return (
        <Row type="flex" style={{flexDirection: 'column'}}>
          <div>{(user.name || '').toLowerCase()}</div>
          <div><span style={{fontSize: 'smaller'}}>{attributesString}</span></div>
        </Row>
      );
    } else {
      return (user.name || '').toLowerCase();
    }
  };

  renderUserName = (user) => {
    if (user.attributes && user.attributes.Name) {
      return <span>{user.attributes.Name}</span>;
    } else {
      return <span>{(user.name || '').toLowerCase()}</span>;
    }
  };

  render () {
    const {
      className,
      showIcon,
      style = {}
    } = this.props;
    if (this.user) {
      return (
        <Tooltip overlay={this.renderUserAttributes(this.user)}>
          <span
            className={className}
            style={Object.assign({cursor: 'default'}, style)}
          >
            {showIcon && <UserOutlined />}
            {this.renderUserName(this.user)}
          </span>
        </Tooltip>
      );
    }
    return (
      <span
        className={className}
        style={Object.assign({cursor: 'default'}, style)}
      >
        {showIcon && <UserOutlined />}
        {(this.props.userName || '').toLowerCase()}
      </span>
    );
  }
}
