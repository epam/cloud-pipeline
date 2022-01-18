/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Provider} from 'mobx-react';
import {Alert} from 'antd';
import Roles from '../../models/user/Roles';
import Users from '../../models/user/Users';
import roleModel from '../../utils/roleModel';
import SubSettings from './sub-settings';
import UsersManagement from './user-management/users';
import GroupsManagement from './user-management/groups';
import UsageReport from './user-management/usage-report/usage-report';

const roles = new Roles();
const usersWithActivity = new Users(true);

function UserManagementForm (
  {
    authenticatedUserInfo,
    router,
    location
  }
) {
  if (!authenticatedUserInfo.loaded && authenticatedUserInfo.pending) {
    return null;
  }
  const isAdmin = authenticatedUserInfo.loaded &&
    authenticatedUserInfo.value &&
    authenticatedUserInfo.value.admin;
  const isReader = roleModel.userHasRole(
    authenticatedUserInfo.loaded ? authenticatedUserInfo.value : undefined,
    'ROLE_USER_READER'
  );
  if (!isReader && !isAdmin) {
    return (
      <Alert type="error" message="Access is denied" />
    );
  }
  return (
    <Provider roles={roles} usersWithActivity={usersWithActivity}>
      <SubSettings
        sections={[
          {
            key: 'users',
            title: 'Users',
            default: true,
            render: () => (
              <UsersManagement />
            )
          },
          {
            key: 'groups',
            title: 'Groups',
            render: () => (
              <GroupsManagement />
            )
          },
          {
            key: 'roles',
            title: 'Roles',
            render: () => (
              <GroupsManagement predefined />
            )
          },
          {
            key: 'report',
            title: 'Usage report',
            render: () => (
              <UsageReport router={router} location={location} />
            )
          }
        ]}
        router={router}
        root="user"
      />
    </Provider>
  );
}

export default roleModel.authenticationInfo(UserManagementForm);
