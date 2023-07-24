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
import {observer, Provider} from 'mobx-react';
import {Alert} from 'antd';
import Roles from '../../models/user/Roles';
import Users from '../../models/user/Users';
import roleModel from '../../utils/roleModel';
import SubSettings from './sub-settings';
import UsersManagement from './user-management/users';
import GroupsManagement from './user-management/groups';
import UsageReport from './user-management/usage-report';
import BillingQuotasList from '../../models/billing/quotas/list';

const roles = new Roles();
const usersWithActivity = new Users(true, true);
const quotas = new BillingQuotasList();

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
  if (!isAdmin && !isReader && usersWithActivity.pending && !usersWithActivity.loaded) {
    return null;
  }
  const users = usersWithActivity.loaded ? usersWithActivity.value : [];
  const userHasReadPermissions = users.some((user) => roleModel.readAllowed(user));
  if (!isReader && !isAdmin && !userHasReadPermissions) {
    return (
      <Alert type="error" message="Access is denied" />
    );
  }
  const sections = [
    (isReader || isAdmin || userHasReadPermissions) ? {
      key: 'users',
      title: 'Users',
      default: true,
      render: () => (
        <UsersManagement />
      )
    } : false,
    (isReader || isAdmin) ? {
      key: 'groups',
      title: 'Groups',
      render: () => (
        <GroupsManagement />
      )
    } : false,
    (isReader || isAdmin) ? {
      key: 'roles',
      title: 'Roles',
      render: () => (
        <GroupsManagement predefined />
      )
    } : false,
    (isReader || isAdmin) ? {
      key: 'report',
      title: 'Usage report',
      render: () => (
        <UsageReport router={router} location={location} />
      )
    } : false
  ].filter(Boolean);
  return (
    <Provider
      roles={roles}
      usersWithActivity={usersWithActivity}
      quotas={quotas}
    >
      <SubSettings
        sections={sections}
        router={router}
        root="user"
        hideListForSingleSection
      />
    </Provider>
  );
}

export default roleModel.authenticationInfo(observer(UserManagementForm));
