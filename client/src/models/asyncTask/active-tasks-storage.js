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

// The list of async tasks this browser is watching for the user, so a task
// queued before a reload - or before the tab was closed for the night - is
// still followed to its end and still announced when it finishes. The browser
// is the only place this can live: there is no server-side "notify me about
// this one" subscription, and the alternative (asking for every task the user
// has, forever, on a timer) is what this replaces.
//
// Nothing here fetches. CurrentUserAsyncTasks.js owns the polling and is the
// only caller.

const STORAGE_KEY = 'activeAsyncTasks';

// How long a queued task stays worth watching, in days: an entry older than
// this is dropped unpolled and unannounced, so a user who queues an archive
// and closes the application for a week is not greeted by a week-old
// completion toast.
//
// **Deliberately `undefined`, which switches the cut-off off entirely**: with
// no number here nothing expires, every watched task is followed to its end
// however long that takes, and a task that finished a month ago is still
// announced on the next visit. The checks are in place and tested
// (`entryIsExpired`, and every caller of it) so that setting this to a number
// of days - 7, say - is the whole change needed to turn the cut-off on. It is
// a code constant rather than a platform preference by choice; that is the
// next step if it ever needs to differ per deployment.
export const ACTIVE_TASK_TTL_DAYS = undefined;

// Every read and write goes through these two: a browser in private mode, or
// one whose quota is full, throws from `localStorage` rather than returning
// nothing, and a toast about a download is never worth taking the page down
// over. When that happens the tracker degrades to this session only, which is
// exactly what it did before anything was persisted.
function readRaw () {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch (error) {
    console.warn('activeAsyncTasks: could not be read', error);
    return undefined;
  }
}

function writeRaw (value) {
  try {
    localStorage.setItem(STORAGE_KEY, value);
  } catch (error) {
    console.warn('activeAsyncTasks: could not be written', error);
  }
}

/**
 * @typedef {Object} ActiveTaskEntry
 * @property {string} id
 * @property {string} [created] the task's own creation date, from the API
 * @property {string} [owner] the user this entry belongs to
 */

/**
 * Every entry currently stored, for every user. Tolerates anything in the
 * key - a hand-edited value, a half-written one, a shape from a future
 * version - by answering with an empty list rather than throwing: a corrupt
 * value must cost the user their notifications, not their session.
 *
 * @returns {ActiveTaskEntry[]}
 */
export function loadEntries () {
  const raw = readRaw();
  if (!raw) {
    return [];
  }
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    console.warn('activeAsyncTasks: ignoring a value that is not JSON', error);
    return [];
  }
  if (!Array.isArray(parsed)) {
    return [];
  }
  return parsed
    .filter(entry => entry && (entry.id !== undefined && entry.id !== null))
    .map(entry => ({
      id: String(entry.id),
      created: entry.created,
      owner: entry.owner
    }));
}

/**
 * @param {ActiveTaskEntry[]} entries
 */
export function saveEntries (entries) {
  writeRaw(JSON.stringify(entries || []));
}

/**
 * Whether an entry is older than the cut-off and so must neither be polled
 * nor announced.
 *
 * Three ways to answer "not expired", and each is a decision rather than a
 * fallback: no cut-off is configured (`ACTIVE_TASK_TTL_DAYS` is `undefined`,
 * which is how this ships - see that constant); the entry carries no
 * `created`, because the task arrived from the API without one; or the date
 * it carries cannot be parsed. In the last two cases dropping the entry would
 * silently lose a task the user is waiting on, which is the worse of the two
 * failures.
 *
 * `ttlDays` is a parameter so the cut-off can be exercised while it is
 * switched off - no caller passes it.
 *
 * @param {ActiveTaskEntry} entry
 * @param {moment.Moment} [now]
 * @param {number} [ttlDays]
 * @returns {boolean}
 */
export function entryIsExpired (entry, now = moment.utc(), ttlDays = ACTIVE_TASK_TTL_DAYS) {
  if (typeof ttlDays !== 'number') {
    return false;
  }
  if (!entry || !entry.created) {
    return false;
  }
  const created = moment.utc(entry.created);
  if (!created.isValid()) {
    return false;
  }
  return now.diff(created, 'days', true) > ttlDays;
}

/**
 * Adds an entry, replacing any entry with the same id, and answers the
 * stored list.
 *
 * @param {ActiveTaskEntry} entry
 * @returns {ActiveTaskEntry[]}
 */
export function addEntry (entry) {
  if (!entry || entry.id === undefined || entry.id === null) {
    return loadEntries();
  }
  const id = String(entry.id);
  const entries = loadEntries()
    .filter(stored => stored.id !== id)
    .concat([{id, created: entry.created, owner: entry.owner}]);
  saveEntries(entries);
  return entries;
}

/**
 * @param {string|number} id
 * @returns {ActiveTaskEntry[]}
 */
export function removeEntry (id) {
  const entries = loadEntries().filter(stored => stored.id !== String(id));
  saveEntries(entries);
  return entries;
}

/**
 * The entries worth polling for `userName`: theirs, and not expired.
 *
 * Both kinds of entry that are not worth polling are deleted here rather than
 * skipped, because this is the one moment the whole list is in hand: the
 * expired ones, and anyone else's. `localStorage` is per browser, not per
 * user, so a shared machine accumulates both users' ids, and another user's
 * are no use to this one - the API answers only the caller's own tasks
 * (`AsyncTaskApiService.load`), and a notification about a stranger's archive
 * would be meaningless. The cost is that the other user loses a pending
 * notification when this one signs in; the browser can only watch for
 * whoever is using it.
 *
 * An entry with no `owner` is deleted too, rather than adopted. It could only
 * come from a version of this file that did not store the field, so there is
 * no telling whose it is - and "the API would refuse someone else's id
 * anyway" does not hold for an administrator, for whom
 * `AsyncTaskApiService.load` answers any task at all: adopting such an entry
 * would mean an admin signing in on a shared browser being told about a
 * stranger's archive.
 *
 * With no `userName` nothing is deleted beyond the expired entries - who the
 * rest belong to cannot be judged yet.
 *
 * @param {string} userName
 * @param {moment.Moment} [now]
 * @param {number} [ttlDays] - see `entryIsExpired`; no caller passes it
 * @returns {ActiveTaskEntry[]}
 */
export function entriesForUser (userName, now = moment.utc(), ttlDays = ACTIVE_TASK_TTL_DAYS) {
  const entries = loadEntries();
  const live = entries.filter(entry => !entryIsExpired(entry, now, ttlDays));
  if (!userName) {
    if (live.length !== entries.length) {
      saveEntries(live);
    }
    return [];
  }
  const mine = live.filter(entry => entry.owner === userName);
  if (mine.length !== entries.length) {
    saveEntries(mine);
  }
  return mine;
}
