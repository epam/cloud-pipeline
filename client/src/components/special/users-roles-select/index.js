/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Select, Icon} from 'antd';
import Roles from '../../../models/user/Roles';
import GroupFind from '../../../models/user/GroupFind';
import UserName from '../UserName';

const MINIMUM_SEARCH_LENGTH = 2;

const roles = new Roles();

function getRoleDisplayName (role) {
  if (!role.predefined && role.name && role.name.toLowerCase().indexOf('role_') === 0) {
    return role.name.substring('role_'.length);
  }
  return role.name;
}

function getDataSourceItemValue (dataSourceItem) {
  const {
    principal,
    name
  } = dataSourceItem;
  return `${principal ? 'USER' : 'ROLE'}:${name}`;
}

function getDataSourceItemFromValue (value) {
  const [
    principal,
    ...name
  ] = (value || '').split(':');
  return {
    principal: /^user$/i.test(principal),
    name: name.join(':')
  };
}

function nameSorter (a, b) {
  const aName = (a?.displayName || a?.name || '').toLowerCase();
  const bName = (b?.displayName || b?.name || '').toLowerCase();
  if (aName < bName) {
    return -1;
  }
  if (aName > bName) {
    return 1;
  }
  return 0;
}

@inject('usersInfo')
@inject(({usersInfo}) => ({roles, users: usersInfo}))
@observer
class UsersRolesSelect extends React.Component {
  state = {
    searchString: undefined,
    adGroups: []
  };

  @computed
  get users () {
    const {users} = this.props;
    if (users && users.loaded) {
      return (users.value || [])
        .map(user => ({
          search: [user.name, ...(Object.values(user.attributes || {}))].map(o => o.toLowerCase()),
          name: user.name,
          principal: true
        }))
        .sort(nameSorter);
    }
    return [];
  }

  @computed
  get roles () {
    const {roles} = this.props;
    if (roles && roles.loaded) {
      return (roles.value || [])
        .map(role => ({
          name: role.name,
          search: [role.name.toLowerCase()],
          displayName: getRoleDisplayName(role),
          principal: false
        }))
        .sort(nameSorter);
    }
    return [];
  }

  get items () {
    const {
      searchString = '',
      adGroups = []
    } = this.state;
    const {
      value = [],
      showRoles = true,
      adGroups: showADGroups = showRoles
    } = this.props;
    const uniqueRoleNames = new Set(this.roles.map(o => o.name));
    const filteredADGroups = adGroups.filter(o => !uniqueRoleNames.has(o.name));
    const usersAndRoles = [
      ...this.users,
      ...(showRoles ? this.roles : []),
      ...(showRoles && showADGroups ? filteredADGroups : [])
    ];
    const itemIsSelected = (item) => !!value
      .find(v => v.name === item.name && v.principal === item.principal);
    const unknownItems = (value || [])
      .filter(o => !usersAndRoles.find(i => i.name === o.name && i.principal === o.principal))
      .map(item => ({
        name: item.name,
        search: [item.name.toLowerCase()],
        displayName: item.name,
        principal: item.principal
      }));
    const items = usersAndRoles.concat(unknownItems);
    if (searchString.length >= MINIMUM_SEARCH_LENGTH) {
      const lowerCasedSearchString = searchString.toLowerCase();
      return items.filter(item => itemIsSelected(item) ||
        !!item.search.find(o => o.includes(lowerCasedSearchString))
      )
        .sort(nameSorter);
    }
    return items
      .filter(itemIsSelected)
      .sort(nameSorter);
  }

  onChangeSearchString = (e) => {
    const {
      showRoles = true,
      adGroups: showADGroups = showRoles
    } = this.props;
    this.setState({
      searchString: e || ''
    }, () => {
      if (showADGroups) {
        GroupFind.findGroups(e)
          .then((groups = []) => {
            if (this.state.searchString === e) {
              this.setState({
                adGroups: groups.map(group => ({
                  name: group,
                  search: [group.toLowerCase()],
                  displayName: group,
                  principal: false
                }))
              });
            }
          });
      }
    });
  };

  onChange = (keys) => {
    const payload = (keys || []).map(getDataSourceItemFromValue);
    this.setState({
      searchString: undefined,
      adGroups: []
    }, () => {
      const {onChange} = this.props;
      onChange && onChange(payload);
    });
  };

  render () {
    const {
      className,
      style,
      disabled: disabledProp,
      users,
      roles,
      value = [],
      showRoles = true,
      popupContainerFn
    } = this.props;
    const {
      searchString = ''
    } = this.state;
    const disabled = disabledProp ||
      (users && users.pending && !users.loaded) ||
      (roles && roles.pending && !roles.loaded);
    const placeholder = showRoles
      ? 'Specify user or group name'
      : 'Specify user name';
    return (
      <Select
        disabled={disabled}
        className={className}
        style={style}
        mode="multiple"
        value={value.map(getDataSourceItemValue)}
        onChange={this.onChange}
        filterOption={false}
        placeholder={this.props.placeholder || ''}
        getPopupContainer={o => popupContainerFn
          ? popupContainerFn(o)
          : o.parentNode
        }
        onSearch={this.onChangeSearchString}
        onBlur={() => this.onChangeSearchString()}
        notFoundContent={
          searchString.length >= MINIMUM_SEARCH_LENGTH
            ? `Nothing found for "${searchString}"`
            : placeholder
        }
      >
        <Select.OptGroup label="Users">
          {
            this.items
              .filter(item => item.principal)
              .map(user => (
                <Select.Option
                  key={getDataSourceItemValue(user)}
                  search={user.search}
                  value={getDataSourceItemValue(user)}
                  title={user.name}
                >
                  <UserName
                    userName={user.name}
                    showIcon
                  />
                </Select.Option>
              ))
          }
        </Select.OptGroup>
        <Select.OptGroup label="Groups and roles">
          {
            this.items
              .filter(item => !item.principal)
              .map(role => (
                <Select.Option
                  key={getDataSourceItemValue(role)}
                  search={role.search}
                  value={getDataSourceItemValue(role)}
                  title={role.name}
                >
                  <Icon type="team" /> {role.displayName}
                </Select.Option>
              ))
          }
        </Select.OptGroup>
      </Select>
    );
  }
}

UsersRolesSelect.propTypes = {
  adGroups: PropTypes.bool,
  showRoles: PropTypes.bool,
  placeholder: PropTypes.string,
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  value: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  onChange: PropTypes.func,
  popupContainerFn: PropTypes.func
};

UsersRolesSelect.defaultProps = {
  adGroups: true,
  showRoles: true
};

export default UsersRolesSelect;
