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
import {renderWithStores, screen, waitFor, flushPromises, stubApi} from '@test';
import PermissionsForm from './PermissionsForm';
import roleModel from '../../utils/roleModel';

// The sub-objects warning: a pipeline's Permissions tab checks that everyone the
// pipeline grants Read and Execute to can also read and execute the docker images
// it uses. Issue #4127: those permissions have to be resolved the way the API
// resolves them — the user itself first, then its groups, then its system roles.

const PIPELINE_ID = 1;
const TOOL_ID = 10;
const GRANT_URL = `/grant?id=${PIPELINE_ID}&aclClass=PIPELINE`;
const TOOL_PERMISSIONS_URL = `/permissions?id=${TOOL_ID}&aclClass=TOOL`;
const ROLES_URL = '/role/loadAll';

const USER_NAME = 'USER';
const GROUP = 'ROLE_GROUP1';
const ROLE = 'ROLE_ADVANCED_USER';

// Extended (6-bit) masks: an allow/deny pair per permission.
const ALLOWS_READ_AND_EXECUTE = roleModel.buildPermissionsMask(1, 0, 0, 0, 1, 0);
const DENIES_EXECUTE = roleModel.buildPermissionsMask(1, 0, 0, 0, 0, 1);
const READ_AND_EXECUTE_TO_CHECK = roleModel.buildPermissionsMask(1, 1, 0, 0, 1, 1);

const ROLES = [
  {id: 1, name: GROUP, predefined: false},
  {id: 2, name: ROLE, predefined: true},
  {id: 3, name: 'ROLE_USER', predefined: true}
];

const rule = (name, principal, mask) => ({sid: {name, principal}, mask});

const stores = (users = []) => ({
  usersInfo: {loaded: true, value: users},
  authenticatedUserInfo: {loaded: true, value: {userName: 'ADMIN', admin: true}}
});

/**
 * The pipeline grants `granted` and the tool carries `toolRules`; `users` is what
 * /users/info reports, i.e. which groups and roles a granted user belongs to.
 */
const renderForm = ({granted, toolRules, users}) => {
  const api = stubApi({
    [GRANT_URL]: {
      entity: {id: PIPELINE_ID, owner: 'ADMIN', aclClass: 'PIPELINE'},
      permissions: granted
    },
    [ROLES_URL]: ROLES,
    [TOOL_PERMISSIONS_URL]: {
      entity: {id: TOOL_ID, owner: 'ADMIN', aclClass: 'TOOL'},
      owner: 'ADMIN',
      permissions: toolRules
    }
  });
  renderWithStores(
    <PermissionsForm
      objectIdentifier={PIPELINE_ID}
      objectType="pipeline"
      subObjectsPermissionsMaskToCheck={READ_AND_EXECUTE_TO_CHECK}
      subObjectsToCheck={[{entityId: TOOL_ID, entityClass: 'TOOL', name: 'tool1'}]}
      subObjectsPermissionsErrorTitle="Permissions issues"
    />,
    stores(users)
  );
  return api;
};

/**
 * Waits until the warnings have been recalculated from everything they depend on:
 * `sidRendered` appears only once the pipeline's own permissions are in, and the
 * tool's permissions are the other half of the check.
 */
const warningsAreResolved = async (api, sidRendered) => {
  await screen.findByText(sidRendered);
  await waitFor(() => {
    expect(api.calls.filter((call) => call.path === TOOL_PERMISSIONS_URL)).toHaveLength(1);
  });
  await flushPromises();
};

const warnings = () => screen.queryAllByText(/denied for/);

describe('PermissionsForm sub-objects warnings', () => {
  it('does not warn when a user\'s group allows what its role denies', async () => {
    // Issue #4127: the group's "allow" is the one the API applies, so granting
    // Execute on the pipeline raises nothing.
    const api = renderForm({
      granted: [rule(USER_NAME, true, ALLOWS_READ_AND_EXECUTE)],
      toolRules: [
        rule(GROUP, false, ALLOWS_READ_AND_EXECUTE),
        rule(ROLE, false, DENIES_EXECUTE)
      ],
      users: [{
        id: 1,
        name: USER_NAME,
        roles: [ROLES[0], ROLES[1]],
        groups: []
      }]
    });
    await warningsAreResolved(api, USER_NAME);
    expect(warnings()).toHaveLength(0);
  });

  it('warns when a user\'s group denies what its role allows', async () => {
    const api = renderForm({
      granted: [rule(USER_NAME, true, ALLOWS_READ_AND_EXECUTE)],
      toolRules: [
        rule(GROUP, false, DENIES_EXECUTE),
        rule(ROLE, false, ALLOWS_READ_AND_EXECUTE)
      ],
      users: [{
        id: 1,
        name: USER_NAME,
        roles: [ROLES[0], ROLES[1]],
        groups: []
      }]
    });
    await warningsAreResolved(api, USER_NAME);
    expect(screen.getByText(/execute denied for/)).toBeInTheDocument();
    expect(screen.getByText('Permissions issues')).toBeInTheDocument();
  });

  it('treats a granted group as a group rather than as a role', async () => {
    // The pipeline grants the group itself; the tool allows the group and denies
    // ROLE_USER, which every user carries — so the group's rule decides.
    const api = renderForm({
      granted: [rule(GROUP, false, ALLOWS_READ_AND_EXECUTE)],
      toolRules: [
        rule(GROUP, false, ALLOWS_READ_AND_EXECUTE),
        rule('ROLE_USER', false, DENIES_EXECUTE)
      ],
      users: []
    });
    // The split name renders only once /role/loadAll is in, which is where the
    // group/role distinction comes from for a non-principal SID.
    await warningsAreResolved(api, 'GROUP1');
    expect(warnings()).toHaveLength(0);
  });

  it('warns when a granted group is itself denied', async () => {
    const api = renderForm({
      granted: [rule(GROUP, false, ALLOWS_READ_AND_EXECUTE)],
      toolRules: [
        rule(GROUP, false, DENIES_EXECUTE),
        rule('ROLE_USER', false, ALLOWS_READ_AND_EXECUTE)
      ],
      users: []
    });
    await warningsAreResolved(api, 'GROUP1');
    expect(screen.getByText(/execute denied for/)).toBeInTheDocument();
  });
});
