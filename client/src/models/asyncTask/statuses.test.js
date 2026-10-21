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

import {
  ACTIVE_STATUSES,
  POLLED_STATUSES,
  taskIsActive,
  taskIsFinished,
  taskCanBeCancelled,
  taskArchiveIsAvailable,
  normalizeStatus,
  archiveNameOf
} from './statuses';

describe('taskIsActive', () => {
  test('true for an active status', () => {
    expect(taskIsActive({status: 'RUNNING'})).toBe(true);
  });
  test('false for a terminal status', () => {
    expect(taskIsActive({status: 'SUCCESS'})).toBe(false);
  });
  test('false for undefined', () => {
    expect(taskIsActive(undefined)).toBe(false);
  });
  test('false for a task with no status', () => {
    expect(taskIsActive({id: 1})).toBe(false);
  });
});

describe('taskIsFinished', () => {
  test('true for SUCCESS', () => {
    expect(taskIsFinished({status: 'SUCCESS'})).toBe(true);
  });
  test('true for FAILED', () => {
    expect(taskIsFinished({status: 'FAILED'})).toBe(true);
  });
  test('false for CANCELLED', () => {
    expect(taskIsFinished({status: 'CANCELLED'})).toBe(false);
  });
  test('false for EXPIRED', () => {
    expect(taskIsFinished({status: 'EXPIRED'})).toBe(false);
  });
  test('false for an active status', () => {
    expect(taskIsFinished({status: 'RUNNING'})).toBe(false);
  });
  test('false for undefined', () => {
    expect(taskIsFinished(undefined)).toBe(false);
  });
  test('false for a task with no status', () => {
    expect(taskIsFinished({id: 1})).toBe(false);
  });
});

describe('taskCanBeCancelled', () => {
  test('true for an active status', () => {
    expect(taskCanBeCancelled({status: 'PENDING'})).toBe(true);
  });
  test('false for a terminal status', () => {
    expect(taskCanBeCancelled({status: 'FAILED'})).toBe(false);
  });
  test('false for undefined', () => {
    expect(taskCanBeCancelled(undefined)).toBe(false);
  });
  test('false for a task with no status', () => {
    expect(taskCanBeCancelled({id: 1})).toBe(false);
  });
});

describe('taskArchiveIsAvailable', () => {
  test('true for SUCCESS', () => {
    expect(taskArchiveIsAvailable({status: 'SUCCESS'})).toBe(true);
  });
  test('false for FAILED', () => {
    expect(taskArchiveIsAvailable({status: 'FAILED'})).toBe(false);
  });
  test('false for undefined', () => {
    expect(taskArchiveIsAvailable(undefined)).toBe(false);
  });
  test('false for a task with no status', () => {
    expect(taskArchiveIsAvailable({id: 1})).toBe(false);
  });
});

describe('normalizeStatus', () => {
  test('upper-cases a lowercase status', () => {
    expect(normalizeStatus('success')).toBe('SUCCESS');
  });
  test('upper-cases a mixed-case status', () => {
    expect(normalizeStatus('Failed')).toBe('FAILED');
  });
  test('leaves an already-uppercase status as-is', () => {
    expect(normalizeStatus('RUNNING')).toBe('RUNNING');
  });
  test('returns undefined for undefined', () => {
    expect(normalizeStatus(undefined)).toBeUndefined();
  });
  test('returns undefined for an empty string', () => {
    expect(normalizeStatus('')).toBeUndefined();
  });
});

describe('archiveNameOf', () => {
  test('returns params.archiveName when present', () => {
    expect(archiveNameOf({id: 1, params: {archiveName: 'my-archive'}})).toBe('my-archive');
  });
  test('falls back to the task id when archiveName is absent', () => {
    expect(archiveNameOf({id: 2, params: {}})).toBe(2);
  });
  test('falls back to the task id when params is missing entirely', () => {
    expect(archiveNameOf({id: 3})).toBe(3);
  });
});

describe('POLLED_STATUSES', () => {
  test('contains all four active statuses', () => {
    expect(POLLED_STATUSES).toEqual(expect.arrayContaining(ACTIVE_STATUSES));
  });
  test('contains PENDING, RUNNING, CANCEL_REQUESTED and STOPPING', () => {
    expect(POLLED_STATUSES).toEqual(expect.arrayContaining([
      'PENDING', 'RUNNING', 'CANCEL_REQUESTED', 'STOPPING'
    ]));
  });
  test('contains SUCCESS and FAILED', () => {
    expect(POLLED_STATUSES).toEqual(expect.arrayContaining(['SUCCESS', 'FAILED']));
  });
  test('does not contain CANCELLED', () => {
    expect(POLLED_STATUSES).not.toContain('CANCELLED');
  });
  test('does not contain EXPIRED', () => {
    expect(POLLED_STATUSES).not.toContain('EXPIRED');
  });
  test('is exactly the active statuses plus SUCCESS and FAILED, order-independently', () => {
    expect([...POLLED_STATUSES].sort()).toEqual(
      [...ACTIVE_STATUSES, 'SUCCESS', 'FAILED'].sort()
    );
  });
});
