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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Select} from 'antd';
import styles from './async-tasks-filters.css';

const commonStyle = {flex: 1};

// A small presentational row with a label, copying the local `Filter`
// component in client/src/components/settings/system-management/system-logs/
// filters.js:39-50 (not exported there, so reimplemented here).
function Filter ({label, children}) {
  return (
    <div className={styles.filter}>
      {label && (<span className={styles.label}>{label}:</span>)}
      {children}
    </div>
  );
}

Filter.propTypes = {
  label: PropTypes.string,
  children: PropTypes.node
};

// This bar renders a single control: the owner filter, and only for an
// administrator. `AsyncTaskFilterVO` on the backend branch documents `owner`
// as ignored for a regular user, who always sees only their own tasks - so
// for anyone else the control could only mislead ("pick another user, get
// your own tasks back"). The statuses filter lives in the Status column's
// own header (async-tasks-list.js), not here - a custom antd Table column
// filter, "like in a runs table".
@inject('authenticatedUserInfo', 'users')
@observer
class AsyncTasksFilters extends React.Component {
  @computed
  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    return !!(
      authenticatedUserInfo &&
      authenticatedUserInfo.loaded &&
      authenticatedUserInfo.value &&
      authenticatedUserInfo.value.admin
    );
  }

  @computed
  get myUserName () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo && authenticatedUserInfo.loaded) {
      return (authenticatedUserInfo.value || {}).userName;
    }
    return undefined;
  }

  // Copies the attributesString-building shape of
  // system-logs/filters.js:109-133.
  get users () {
    const {users} = this.props;
    if (users && users.loaded) {
      const mapUserAttributes = (user) => {
        if (user.attributes) {
          const values = [];
          for (const key in user.attributes) {
            if (user.attributes.hasOwnProperty(key)) {
              values.push(user.attributes[key]);
            }
          }
          return values.join(', ');
        }
        return user.userName;
      };
      return (users.value || []).map((user) => ({
        ...user,
        attributesString: mapUserAttributes(user) || ''
      }));
    }
    return [];
  }

  getUserLabel = (user) => (
    <span>{user.userName}{user.userName === this.myUserName ? <b> (you)</b> : null}</span>
  );

  // `undefined` for a cleared selection, never an empty string: the parent
  // deletes the `owner` key entirely rather than sending one that is falsy -
  // see DownloadsManager's `setFilter`.
  handleChange = (value) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(value || undefined);
    }
  };

  render () {
    if (!this.isAdmin) {
      return null;
    }
    const {owner} = this.props;
    return (
      <div className={styles.filters}>
        <Filter label="Owner">
          <Select
            allowClear
            showSearch
            dropdownMatchSelectWidth={false}
            optionLabelProp="label"
            placeholder="Any user"
            style={commonStyle}
            filterOption={
              (input, option) => {
                const search = input.toLowerCase();
                const value = (option.props.value || '').toLowerCase();
                const attributes = (option.props.attributesString || '').toLowerCase();
                return value.includes(search) || attributes.includes(search);
              }
            }
            value={owner}
            onChange={this.handleChange}
          >
            {
              this.users.map((user) => (
                <Select.Option
                  key={user.userName}
                  value={user.userName}
                  attributesString={user.attributesString}
                  label={this.getUserLabel(user)}
                >
                  {this.getUserLabel(user)}
                </Select.Option>
              ))
            }
          </Select>
        </Filter>
      </div>
    );
  }
}

AsyncTasksFilters.propTypes = {
  owner: PropTypes.string,
  onChange: PropTypes.func
};

AsyncTasksFilters.defaultProps = {
  onChange: () => {}
};

export default AsyncTasksFilters;
