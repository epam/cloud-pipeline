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
import {Row, Tooltip} from 'antd';

@inject('users')
@observer
export default class UserName extends React.Component {

  static propTypes = {
    userName: PropTypes.string
  };

  @computed
  get user () {
    if (this.props.users.loaded && this.props.userName) {
      const [user] = (this.props.users.value || [])
        .filter(u => u.userName.toLowerCase() === this.props.userName.toLowerCase());
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
          <Row>{(user.userName || '').toLowerCase()}</Row>
          <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
        </Row>
      );
    } else {
      return (user.userName || '').toLowerCase();
    }
  };

  renderUserName = (user) => {
    if (user.attributes && user.attributes.Name) {
      return <span>{user.attributes.Name}</span>;
    } else {
      return <span>{(user.userName || '').toLowerCase()}</span>;
    }
  };

  render () {
    if (this.user) {
      return (
        <Tooltip overlay={this.renderUserAttributes(this.user)}>
          <span style={{cursor: 'default'}}>
            {this.renderUserName(this.user)}
          </span>
        </Tooltip>
      );
    }
    return <span>{(this.props.userName || '').toLowerCase()}</span>;
  }

}
