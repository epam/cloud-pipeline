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

import moment from 'moment-timezone';
import {spyOn} from '@test';
import {
  ACTIVE_TASK_TTL_DAYS,
  addEntry,
  entriesForUser,
  entryIsExpired,
  loadEntries,
  removeEntry,
  saveEntries
} from './active-tasks-storage';

const KEY = 'activeAsyncTasks';
const USER = 'current-user';
// Fixed, and read through moment so the timezone pin applies (see
// client/test/AGENTS.md on raw Date methods).
const NOW = moment.utc('2026-10-23T12:00:00.000Z');

function daysBeforeNow (days) {
  return NOW.clone().subtract(days, 'days').toISOString();
}

function storedRaw () {
  return JSON.parse(localStorage.getItem(KEY));
}

beforeEach(() => {
  localStorage.clear();
});

describe('loadEntries', () => {
  it('answers an empty list when nothing is stored', () => {
    expect(loadEntries()).toEqual([]);
  });

  it('reads back what saveEntries wrote, with ids as strings', () => {
    saveEntries([{id: 7, created: daysBeforeNow(1), owner: USER}]);

    expect(loadEntries()).toEqual([
      {id: '7', created: daysBeforeNow(1), owner: USER}
    ]);
  });

  // A value nobody here wrote - hand-edited, half-written, or from a later
  // version of this file - must cost the user their notifications, not their
  // session.
  it('ignores a value that is not JSON', () => {
    localStorage.setItem(KEY, 'not json at all');

    expect(loadEntries()).toEqual([]);
  });

  it('ignores a value that is JSON but not an array', () => {
    localStorage.setItem(KEY, JSON.stringify({id: '1'}));

    expect(loadEntries()).toEqual([]);
  });

  it('drops entries with no id', () => {
    localStorage.setItem(KEY, JSON.stringify([{created: daysBeforeNow(1)}, {id: '3'}]));

    expect(loadEntries().map(entry => entry.id)).toEqual(['3']);
  });
});

describe('addEntry / removeEntry', () => {
  it('appends an entry and persists it', () => {
    addEntry({id: '1', created: daysBeforeNow(1), owner: USER});
    addEntry({id: '2', created: daysBeforeNow(1), owner: USER});

    expect(storedRaw().map(entry => entry.id)).toEqual(['1', '2']);
  });

  it('replaces an entry with the same id rather than duplicating it', () => {
    addEntry({id: '1', created: daysBeforeNow(2), owner: USER});
    addEntry({id: 1, created: daysBeforeNow(1), owner: USER});

    const stored = storedRaw();
    expect(stored).toHaveLength(1);
    expect(stored[0].created).toBe(daysBeforeNow(1));
  });

  it('removes by id, string or number', () => {
    addEntry({id: '1', owner: USER});
    addEntry({id: '2', owner: USER});

    removeEntry(1);

    expect(storedRaw().map(entry => entry.id)).toEqual(['2']);
  });
});

describe('entryIsExpired', () => {
  // How this ships: no cut-off is configured, so nothing expires however old
  // it is, and a task that finished a month ago is still announced. The tests
  // below pass a number of days explicitly, which is the one change needed to
  // turn the cut-off on.
  it('expires nothing with the cut-off switched off, which is the default', () => {
    expect(ACTIVE_TASK_TTL_DAYS).toBeUndefined();
    expect(entryIsExpired({id: '1', created: daysBeforeNow(400)}, NOW)).toBe(false);
  });

  it('is false just inside a configured cut-off and true just outside it', () => {
    expect(entryIsExpired({id: '1', created: daysBeforeNow(6)}, NOW, 7)).toBe(false);
    expect(entryIsExpired({id: '1', created: daysBeforeNow(8)}, NOW, 7)).toBe(true);
  });

  // Treating an unknown creation date as expired would silently lose a task
  // the user is waiting on - the worse of the two failures.
  it('treats a missing or unparseable created date as live', () => {
    expect(entryIsExpired({id: '1'}, NOW, 7)).toBe(false);
    expect(entryIsExpired({id: '1', created: 'whenever'}, NOW, 7)).toBe(false);
  });
});

describe('entriesForUser', () => {
  it('answers this user\'s live entries', () => {
    saveEntries([
      {id: '1', created: daysBeforeNow(1), owner: USER},
      {id: '2', created: daysBeforeNow(1), owner: 'someone-else'}
    ]);

    expect(entriesForUser(USER, NOW).map(entry => entry.id)).toEqual(['1']);
  });

  it('drops entries past a configured cut-off from storage as it goes', () => {
    saveEntries([
      {id: 'fresh', created: daysBeforeNow(1), owner: USER},
      {id: 'stale', created: daysBeforeNow(9), owner: USER}
    ]);

    expect(entriesForUser(USER, NOW, 7).map(entry => entry.id)).toEqual(['fresh']);
    expect(storedRaw().map(entry => entry.id)).toEqual(['fresh']);
  });

  it('keeps an old entry while the cut-off is switched off', () => {
    saveEntries([{id: 'ancient', created: daysBeforeNow(400), owner: USER}]);

    expect(entriesForUser(USER, NOW).map(entry => entry.id)).toEqual(['ancient']);
    expect(storedRaw().map(entry => entry.id)).toEqual(['ancient']);
  });

  // localStorage is per browser, not per user: two people sharing a machine
  // otherwise accumulate each other's ids, which this user can neither poll
  // (the API answers only the caller's own tasks) nor be meaningfully told
  // about. They are deleted, not skipped.
  it('deletes another user\'s entries', () => {
    saveEntries([
      {id: '1', created: daysBeforeNow(1), owner: USER},
      {id: '2', created: daysBeforeNow(1), owner: 'someone-else'}
    ]);

    entriesForUser(USER, NOW);

    expect(storedRaw().map(entry => entry.id)).toEqual(['1']);
  });

  it('answers nothing, and deletes nobody\'s entries, when the user is unknown', () => {
    saveEntries([{id: '1', created: daysBeforeNow(1), owner: USER}]);

    expect(entriesForUser(undefined, NOW)).toEqual([]);
    expect(storedRaw().map(entry => entry.id)).toEqual(['1']);
  });

  // An entry with no owner could only come from a version that did not store
  // one, so there is no telling whose it is - and an administrator can load
  // any task at all, so adopting it could mean being told about a stranger's
  // archive.
  it('deletes an entry with no owner rather than adopting it', () => {
    saveEntries([{id: '1', created: daysBeforeNow(1)}]);

    expect(entriesForUser(USER, NOW)).toEqual([]);
    expect(storedRaw()).toEqual([]);
  });
});

// A browser in private mode, or one out of quota, throws from `localStorage`
// rather than answering nothing. A toast about a download is never worth
// taking the page down over, so both directions are swallowed and the tracker
// degrades to this session only.
describe('a localStorage that throws', () => {
  // Spied on the prototype rather than the instance: jsdom's `localStorage`
  // is an accessor on `window` whose own methods are not writable, so
  // `spyOn(localStorage, ...)` cannot replace them. `Object.getPrototypeOf`
  // rather than `__proto__`, which this repo's lint rules forbid.
  function spyOnStorage (method) {
    return spyOn(Object.getPrototypeOf(window.localStorage), method)
      .mockImplementation(() => { throw new Error('denied'); });
  }

  it('reads as empty rather than throwing', () => {
    const getItem = spyOnStorage('getItem');
    try {
      expect(loadEntries()).toEqual([]);
    } finally {
      getItem.mockRestore();
    }
  });

  it('writes without throwing', () => {
    const setItem = spyOnStorage('setItem');
    try {
      expect(() => addEntry({id: '1', owner: USER})).not.toThrow();
    } finally {
      setItem.mockRestore();
    }
  });
});
