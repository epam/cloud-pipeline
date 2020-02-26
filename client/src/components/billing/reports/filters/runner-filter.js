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
import {Select} from 'antd';
import UserName from '../../../special/UserName';

const RunnerType = {
  user: 'user',
  group: 'group'
};

function runnerFilter (
  {
    billingCenters: billingCentersRequest,
    onChange,
    filter,
    users: usersRequest
  }
) {
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
      style={{width: 200}}
      placeholder="All users / groups"
      value={currentRunner}
      onChange={changeRunner}
    >
      <Select.OptGroup label="Users">
        {
          users.map((user) => (
            <Select.Option
              key={`${RunnerType.user}_${user.id}`}
              value={`${RunnerType.user}_${user.id}`}
            >
              <UserName userName={user.userName} />
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
            >
              {center}
            </Select.Option>
          ))
        }
      </Select.OptGroup>
    </Select>
  );
}

export default inject('billingCenters', 'users')(observer(runnerFilter));
export {RunnerType};
