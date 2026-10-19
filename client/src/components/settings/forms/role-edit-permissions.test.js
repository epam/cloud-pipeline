/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {isRoleReadOnly, isUserDefaultEditable} from './role-edit-permissions';

// The plain 3-bit mask the API puts on an entity.
const READ = 1;
const WRITE = 2;

describe('isRoleReadOnly', () => {
  const group = {id: 1, predefined: false, mask: READ | WRITE};
  const predefinedRole = {id: 2, predefined: true, mask: READ | WRITE};

  test('is editable for an admin and a user admin', () => {
    expect(isRoleReadOnly({admin: true, role: predefinedRole})).toBe(false);
    expect(isRoleReadOnly({usersAdmin: true, role: predefinedRole})).toBe(false);
  });

  test('is editable for a user with WRITE permission on a group', () => {
    expect(isRoleReadOnly({role: group})).toBe(false);
  });

  test('is read-only for a user with READ permission on a group', () => {
    expect(isRoleReadOnly({role: {...group, mask: READ}})).toBe(true);
  });

  test('is read-only for a user with WRITE permission on a predefined role', () => {
    expect(isRoleReadOnly({role: predefinedRole})).toBe(true);
  });

  test('is read-only when there is no role', () => {
    expect(isRoleReadOnly()).toBe(true);
  });
});

describe('isUserDefaultEditable', () => {
  test('allows an admin', () => {
    expect(isUserDefaultEditable({admin: true})).toBe(true);
  });

  test('allows a user admin', () => {
    expect(isUserDefaultEditable({usersAdmin: true})).toBe(true);
  });

  test('denies a user with WRITE permission on the group only', () => {
    expect(isUserDefaultEditable({})).toBe(false);
    expect(isUserDefaultEditable()).toBe(false);
  });

  test('denies while the dialog is read-only', () => {
    expect(isUserDefaultEditable({admin: true, readOnly: true})).toBe(false);
  });

  test('denies while an operation is in progress', () => {
    expect(isUserDefaultEditable({usersAdmin: true, pending: true})).toBe(false);
  });
});
