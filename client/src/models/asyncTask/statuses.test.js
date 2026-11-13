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
  ALL_ASYNC_TASK_STATUSES,
  ASYNC_TASK_STATUSES,
  ACTIVE_STATUSES,
  POLLED_STATUSES,
  taskIsActive,
  taskIsFinished,
  taskCanBeCancelled,
  taskArchiveIsAvailable,
  normalizeStatus,
  archiveNameOf,
  statusesAreFiltered,
  ownerIsFiltered,
  ASYNC_TASK_TYPES,
  normalizeType,
  typeOf,
  displayTaskType,
  taskIsDownloadArchive,
  taskIsUnpackArchive,
  extractedArchiveNameOf,
  typeIsFiltered
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
  test('true for a successful download archive', () => {
    expect(taskArchiveIsAvailable({type: 'DOWNLOAD_ARCHIVE', status: 'SUCCESS'})).toBe(true);
  });
  test('false for FAILED', () => {
    expect(taskArchiveIsAvailable({type: 'DOWNLOAD_ARCHIVE', status: 'FAILED'})).toBe(false);
  });
  // `/async-task/{id}/consume` throws for an extraction, so a successful one
  // still has nothing to hand over.
  test('false for a successful extraction', () => {
    expect(taskArchiveIsAvailable({type: 'UNPACK_ARCHIVE', status: 'SUCCESS'})).toBe(false);
  });
  test('false for undefined', () => {
    expect(taskArchiveIsAvailable(undefined)).toBe(false);
  });
  test('false for a task with no status', () => {
    expect(taskArchiveIsAvailable({type: 'DOWNLOAD_ARCHIVE', id: 1})).toBe(false);
  });
  test('false for a task with no type', () => {
    expect(taskArchiveIsAvailable({status: 'SUCCESS'})).toBe(false);
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

// The list the Downloads modal's Status filter renders a checkbox for, and
// what its select-all box ticks. It lives in this module rather than in that
// dropdown's own file so the component and its tests read the same list;
// these cases are what hold it to the vocabulary.
//
// Note what is NOT asserted here, and cannot be: every key in
// ASYNC_TASK_STATUSES maps to a string equal to itself, so `Object.values`
// and `Object.keys` return the same array, and no test in this file can tell
// which one the implementation used. Writing one that looks like it does
// would be worse than leaving it out - the cases below assert the list's
// contents and shape instead, which is what callers actually depend on.
describe('ALL_ASYNC_TASK_STATUSES', () => {
  test('holds every value in ASYNC_TASK_STATUSES, and nothing else', () => {
    expect([...ALL_ASYNC_TASK_STATUSES].sort()).toEqual(Object.values(ASYNC_TASK_STATUSES).sort());
  });
  test('is a superset of POLLED_STATUSES, and strictly larger', () => {
    expect(ALL_ASYNC_TASK_STATUSES).toEqual(expect.arrayContaining(POLLED_STATUSES));
    expect(ALL_ASYNC_TASK_STATUSES.length).toBeGreaterThan(POLLED_STATUSES.length);
  });
  test('contains the two the default leaves out', () => {
    expect(ALL_ASYNC_TASK_STATUSES).toContain('CANCELLED');
    expect(ALL_ASYNC_TASK_STATUSES).toContain('EXPIRED');
  });
  test('carries no duplicates, so a count against it is a count of distinct statuses', () => {
    expect(new Set(ALL_ASYNC_TASK_STATUSES).size).toBe(ALL_ASYNC_TASK_STATUSES.length);
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

// The shared "differs from default" comparisons the Status/Owner column
// filter icons (async-tasks-list.js) and the "Reset filters" control
// (DownloadsManager.js) both call, so the two can never independently decide
// what "default" means - see each function's own comment in ./statuses.js.
describe('statusesAreFiltered', () => {
  test('false when the selection equals the default', () => {
    expect(statusesAreFiltered(POLLED_STATUSES, POLLED_STATUSES)).toBe(false);
  });
  test('false when the selection equals the default in a different order', () => {
    expect(statusesAreFiltered([...POLLED_STATUSES].reverse(), POLLED_STATUSES)).toBe(false);
  });
  test('false for an empty selection against an empty default', () => {
    expect(statusesAreFiltered([], [])).toBe(false);
  });
  // An empty selection is a real, committable filter now - "no status is
  // ticked" - so it has to read as filtered against a non-empty default.
  // Without this, unticking everything would leave the column's header icon
  // unlit and DownloadsManager's "Reset filters" button hidden, which is the
  // one gesture that gets the default back.
  test('true for an empty selection against a non-empty default', () => {
    expect(statusesAreFiltered([], POLLED_STATUSES)).toBe(true);
  });
  test('true when the selection differs from the default', () => {
    expect(statusesAreFiltered([...POLLED_STATUSES, 'CANCELLED'], POLLED_STATUSES)).toBe(true);
  });
  test('treats a missing selection as empty, not a throw', () => {
    expect(statusesAreFiltered(undefined, POLLED_STATUSES)).toBe(true);
  });
  test('defaults the comparison target to POLLED_STATUSES when none is given', () => {
    expect(statusesAreFiltered(POLLED_STATUSES)).toBe(false);
    expect(statusesAreFiltered(['CANCELLED'])).toBe(true);
  });
});

describe('ownerIsFiltered', () => {
  test('false when the owner equals defaultOwner, even though a value is set', () => {
    expect(ownerIsFiltered('current-user', 'current-user')).toBe(false);
  });
  test('true when the owner differs from defaultOwner', () => {
    expect(ownerIsFiltered('someone-else', 'current-user')).toBe(true);
  });
  test('false when neither owner nor defaultOwner is set', () => {
    expect(ownerIsFiltered(undefined, undefined)).toBe(false);
  });
  test('false while defaultOwner is not yet known, regardless of owner', () => {
    expect(ownerIsFiltered('someone-else', undefined)).toBe(false);
    expect(ownerIsFiltered(undefined, undefined)).toBe(false);
  });
  test('true when owner is cleared but defaultOwner is known', () => {
    expect(ownerIsFiltered(undefined, 'current-user')).toBe(true);
  });
});

describe('normalizeType', () => {
  // The same defence `normalizeStatus` has: the server's own casing is not
  // something the comparisons below should depend on.
  test('upper-cases, and leaves nothing for a falsy type', () => {
    expect(normalizeType('unpack_archive')).toBe('UNPACK_ARCHIVE');
    expect(normalizeType('UNPACK_ARCHIVE')).toBe('UNPACK_ARCHIVE');
    expect(normalizeType(undefined)).toBeUndefined();
    expect(normalizeType('')).toBeUndefined();
  });
});

describe('typeOf', () => {
  test('reads the task\'s type, normalized', () => {
    expect(typeOf({type: 'download_archive'})).toBe('DOWNLOAD_ARCHIVE');
    expect(typeOf({})).toBeUndefined();
    expect(typeOf(undefined)).toBeUndefined();
  });
});

describe('displayTaskType', () => {
  test('names the known types', () => {
    expect(displayTaskType(ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE)).toBe('Download archive');
    expect(displayTaskType(ASYNC_TASK_TYPES.UNPACK_ARCHIVE)).toBe('Extract archive');
    expect(displayTaskType('unpack_archive')).toBe('Extract archive');
  });

  // Rather than an empty cell for a type this GUI does not know yet.
  test('falls back to the raw value for an unknown type', () => {
    expect(displayTaskType('SOMETHING_ELSE')).toBe('SOMETHING_ELSE');
    expect(displayTaskType(undefined)).toBeUndefined();
  });
});

describe('taskIsDownloadArchive / taskIsUnpackArchive', () => {
  test.each([
    ['DOWNLOAD_ARCHIVE', true, false],
    ['download_archive', true, false],
    ['UNPACK_ARCHIVE', false, true],
    ['SOMETHING_ELSE', false, false]
  ])('%s', (type, isDownload, isUnpack) => {
    expect(taskIsDownloadArchive({type})).toBe(isDownload);
    expect(taskIsUnpackArchive({type})).toBe(isUnpack);
  });

  test('a task with no type is neither', () => {
    expect(taskIsDownloadArchive({})).toBe(false);
    expect(taskIsUnpackArchive(undefined)).toBe(false);
  });
});

describe('extractedArchiveNameOf', () => {
  test('is the leaf of sourcePath', () => {
    expect(extractedArchiveNameOf({params: {sourcePath: 'a/b/data.tar.gz'}})).toBe('data.tar.gz');
    expect(extractedArchiveNameOf({params: {sourcePath: 'data.zip'}})).toBe('data.zip');
  });

  // An extraction has no `archiveName`, so the id is all that is left.
  test('falls back to the task id', () => {
    expect(extractedArchiveNameOf({id: 'task-1', params: {}})).toBe('task-1');
    expect(extractedArchiveNameOf({id: 'task-1'})).toBe('task-1');
  });
});

describe('typeIsFiltered', () => {
  // The default is every type, which the request expresses by sending none.
  test('only a set type counts as filtered', () => {
    expect(typeIsFiltered('UNPACK_ARCHIVE')).toBe(true);
    expect(typeIsFiltered(undefined)).toBe(false);
    expect(typeIsFiltered('')).toBe(false);
  });
});
