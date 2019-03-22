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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {AutoComplete, Row} from 'antd';
import UserFind from '../../models/user/UserFind';

@inject('authenticatedUserInfo')
@observer
export default class UserAutoComplete extends React.Component {

  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    readOnly: PropTypes.bool,
    placeholder: PropTypes.string,
    onPressEnter: PropTypes.func,
    size: PropTypes.oneOf(['default', 'large', 'small'])
  };

  state = {
    value: null,
    valueInput: null,
    fetching: false,
    fetchedUsers: [],
    operationInProgress: false
  };

  operationWrapper = (operation) => (...props) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        operationInProgress: false
      });
    });
  };
  lastFetchId = 0;
  findUser = (value) => {
    this.lastFetchId += 1;
    const fetchId = this.lastFetchId;
    this.setState({
      valueInput: value,
      value: null,
      fetching: true,
      selectedUser: null
    }, async () => {
      const request = new UserFind(value);
      await request.fetch();
      if (fetchId === this.lastFetchId) {
        let fetchedUsers = [];
        if (!request.error) {
          fetchedUsers = (request.value || []).map(u => u);
        }
        this.setState({
          fetching: false,
          fetchedUsers
        });
      }
    });
  };
  onBlur = () => {
    if (this.state.value === null) {
      this.setState({
        valueInput: null
      });
    }
  };
  onUserSelect = (key) => {
    const [user] = this.state.fetchedUsers.filter(u => `${u.id}` === `${key}`);
    if (user) {
      this.setState({
        valueInput: user.userName,
        value: user.userName
      }, () => {
        this.props.onChange && this.props.onChange(this.state.value);
      });
    }
  };
  renderUserName = (user) => {
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
          <Row>{user.userName}{user.userName === this.myUserName ? <b> (you)</b> : undefined}</Row>
          <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
        </Row>
      );
    } else {
      return user.userName;
    }
  };

  @computed
  get myUserName () {
    if (this.props.authenticatedUserInfo.loaded) {
      return this.props.authenticatedUserInfo.value.userName;
    }
    return undefined;
  }

  render () {
    return (
      <div
        onKeyDown={(e) => {
          if (e.key && e.key.toLowerCase() === 'enter') {
            this.props.onPressEnter && this.props.onPressEnter();
          }
        }}
        style={{flex: 1, width: '100%'}}>
        <AutoComplete
          getPopupContainer={triggerNode => triggerNode.parentNode}
          readOnly={this.props.readOnly || this.state.operationInProgress}
          size={this.props.size || 'default'}
          style={{flex: 1, width: '100%'}}
          placeholder={this.props.placeholder}
          optionLabelProp="text"
          value={this.state.valueInput !== null ? this.state.valueInput : this.props.value}
          onPressEnter={this.props.onPressEnter}
          onBlur={this.onBlur}
          onSelect={this.onUserSelect}
          onSearch={this.operationWrapper(this.findUser)}>
          {
            this.state.fetchedUsers.map(user => {
              return (
                <AutoComplete.Option
                  key={user.id}
                  text={user.userName}>
                  {this.renderUserName(user)}
                </AutoComplete.Option>
              );
            })
          }
        </AutoComplete>
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.value !== this.props.value) {
      this.setState({
        value: null,
        valueInput: null,
        fetchedUsers: [],
        fetching: false
      });
    }
  }
}
