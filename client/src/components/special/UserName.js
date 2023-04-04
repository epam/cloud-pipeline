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
import {Icon, Row, Tooltip} from 'antd';
import {compareUserNames, compareUserNamesWithoutDomain} from '../../utils/users-filters';

function getAttribute (attributes, ...attribute) {
  const variants = attribute.map((attr) => ([
    attr,
    attr.toLowerCase(),
    attr.toUpperCase(),
    attr.slice(0, 1).toUpperCase().concat(attr.slice(1)),
    attr.slice(0, 1).toLowerCase().concat(attr.slice(1))
  ])).reduce((r, c) => ([...r, ...c]), []);
  const [result] = variants
    .map((variant) => (attributes || {})[variant])
    .filter((value) => value !== undefined);
  return result;
}

@inject('usersInfo')
@observer
export default class UserName extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    userName: PropTypes.string,
    style: PropTypes.object,
    showIcon: PropTypes.bool,
    tooltipPlacement: PropTypes.string
  };

  @computed
  get user () {
    if (this.props.usersInfo.loaded && this.props.userName) {
      const user = (this.props.usersInfo.value || [])
        .find(u => compareUserNames(u.name, this.props.userName));
      if (user) {
        return user;
      }
      return (this.props.usersInfo.value || [])
        .find(u => compareUserNamesWithoutDomain(u.name, this.props.userName));
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
          <Row>{(user.name || '').toLowerCase()}</Row>
          <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
        </Row>
      );
    } else {
      return (user.name || '').toLowerCase();
    }
  };

  renderUserName = (user) => {
    const {
      attributes = {},
      name,
      userName
    } = user;
    const firstName = getAttribute(attributes, 'FirstName', 'First Name');
    const lastName = getAttribute(attributes, 'LastName', 'Last Name');
    const attrName = getAttribute(attributes, 'name');
    if (firstName && lastName && firstName !== lastName) {
      return `${lastName} ${firstName}`;
    }
    if (firstName && lastName) {
      return firstName;
    }
    if (attrName) {
      return attrName;
    }
    if (name) {
      return name;
    }
    return (userName || '').toLowerCase();
  };

  render () {
    const {
      className,
      showIcon,
      style = {},
      tooltipPlacement
    } = this.props;
    if (this.user) {
      return (
        <Tooltip
          overlay={this.renderUserAttributes(this.user)}
          placement={tooltipPlacement}
        >
          <span
            className={className}
            style={Object.assign({cursor: 'default'}, style)}
          >
            {showIcon && <Icon type="user" />}
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
        {showIcon && <Icon type="user" />}
        {(this.props.userName || '').toLowerCase()}
      </span>
    );
  }
}
