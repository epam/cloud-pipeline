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

import {getRoleSidName, splitRoleName} from './utilities';

const roles = [
  {name: 'ROLE_USER', predefined: true},
  {name: 'ROLE_ADMIN', predefined: true},
  {name: 'ROLE_GPU_USERS', predefined: false}
];

describe('getRoleSidName', () => {
  test('maps a custom role picked by its displayed name back to its full name', () => {
    expect(splitRoleName(roles[2])).toBe('GPU_USERS');
    expect(getRoleSidName('GPU_USERS', roles)).toBe('ROLE_GPU_USERS');
  });

  test('keeps a predefined role, which is displayed with its full name', () => {
    expect(getRoleSidName('ROLE_USER', roles)).toBe('ROLE_USER');
  });

  test('keeps a name that is no role, e.g. a group found by search', () => {
    expect(getRoleSidName('LAB_GROUP', roles)).toBe('LAB_GROUP');
  });

  test('does not strip a predefined role that matches the shortened form', () => {
    // 'USER' is not how ROLE_USER is displayed, so it must not turn into ROLE_USER
    expect(getRoleSidName('USER', roles)).toBe('USER');
  });

  test('keeps the name when the roles are not loaded', () => {
    expect(getRoleSidName('GPU_USERS', undefined)).toBe('GPU_USERS');
    expect(getRoleSidName('GPU_USERS', [])).toBe('GPU_USERS');
  });
});
