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
import {observer, inject} from 'mobx-react';
import {Row, Select} from 'antd';

const RunnerType = {
  user: 'user',
  group: 'group'
};

function getUserDescription (user, myUserName) {
  if (!user) {
    return null;
  }
  return `${user.userName}${user.userName === myUserName ? ' (you)' : ''}`;
}

function getUserSearchOptions (user) {
  if (user) {
    const searchParts = [
      (user.userName || '').toLowerCase()
    ];
    if (user.attributes) {
      const getAttributesValues = () => {
        const values = [];
        for (let key in user.attributes) {
          if (user.attributes.hasOwnProperty(key)) {
            values.push((user.attributes[key] || '').toLowerCase());
          }
        }
        return values;
      };
      searchParts.push(...getAttributesValues());
    }
    return searchParts;
  }
  return [];
}

function filterRunner (searchOptions, filter) {
  if (!filter) {
    return true;
  }
  return searchOptions.filter(p => p.indexOf(filter.toLowerCase()) >= 0).length > 0;
}

function RenderUserName ({user, myUserName}) {
  if (!user) {
    return null;
  }
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
        <Row>{user.userName}{user.userName === myUserName ? <b> (you)</b> : undefined}</Row>
        <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
      </Row>
    );
  }
  return (
    <span>
      {user.userName}
    </span>
  );
}

function runnerFilter (
  {
    authenticatedUserInfo,
    billingCenters: billingCentersRequest,
    onChange,
    filter,
    users: usersRequest
  }
) {
  const myUserName = authenticatedUserInfo && authenticatedUserInfo.loaded
    ? authenticatedUserInfo.value.userName
    : undefined;
  const changeRunner = (key) => {
    if (key) {
      const [type, ...rest] = key.split('_');
      onChange && onChange({id: rest.join('_'), type});
    } else {
      onChange && onChange(null);
    }
  };
  let users = [];
  if (usersRequest && usersRequest.loaded) {
    users = (usersRequest.value || []).map(u => u);
  }
  let centers = [];
  if (billingCentersRequest && billingCentersRequest.loaded) {
    centers = (billingCentersRequest.value || []).map(c => c);
  }
  let currentRunner;
  if (filter) {
    const {type, id} = filter;
    currentRunner = `${type}_${id}`;
  }
  return (
    <Select
      allowClear
      showSearch
      dropdownMatchSelectWidth={false}
      style={{width: 200}}
      placeholder="All users / groups"
      value={currentRunner}
      onChange={changeRunner}
      optionLabelProp="text"
      filterOption={
        (input, option) => filterRunner(option.props.searchOptions, input)
      }
    >
      <Select.OptGroup label="Users">
        {
          users.map((user) => (
            <Select.Option
              key={`${RunnerType.user}_${user.id}`}
              value={`${RunnerType.user}_${user.id}`}
              text={getUserDescription(user, myUserName)}
              user={user}
              searchOptions={getUserSearchOptions(user)}
            >
              <RenderUserName myUserName={myUserName} user={user} />
            </Select.Option>
          ))
        }
      </Select.OptGroup>
      <Select.OptGroup label="Billing centers">
        {
          centers.map((center) => (
            <Select.Option
              key={`${RunnerType.group}_${center}`}
              value={`${RunnerType.group}_${center}`}
              text={center}
              searchOptions={[(center || '').toLowerCase()]}
            >
              {center}
            </Select.Option>
          ))
        }
      </Select.OptGroup>
    </Select>
  );
}

export default inject('authenticatedUserInfo', 'billingCenters', 'users')(observer(runnerFilter));
export {RunnerType};
