/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {computed, observable} from 'mobx';
import LineStates from '../conflicted-file/line-states';
import ModificationType from './types';
import ChangeStatuses from './statuses';
import {Merged} from '../conflicted-file/branches';

const modificationStates = new Set([
  LineStates.inserted,
  LineStates.removed,
  LineStates.conflictStart,
  LineStates.conflictEnd
]);

/**
 * Describes git change
 */
export default class Change {
  /**
   * Returns true if line indicates some sort of change (insertion, deletion, conflict)
   * @param line {ConflictedFileLine}
   * @param branch {string}
   * @returns {boolean}
   */
  static lineIndicatesChange (line, branch) {
    return modificationStates.has(line.state[branch]);
  }
  /**
   * @type {ConflictedFile}
   */
  conflictedFile;
  /**
   * Change start line
   * @type {ConflictedFileLine}
   */
  start;
  /**
   * Change end line
   * @type {ConflictedFileLine}
   */
  end;
  /**
   * Branch identifier
   * @type {string}
   */
  branch;
  /**
   * Identifies whether change is a conflict
   * @type {boolean}
   */
  @observable conflict = false;
  /**
   * Change type
   * @type {string}
   */
  type;

  /**
   * List of parent changes; applicable only for conflicts
   * @type {Change[]}
   * @private
   */
  @observable _parentChanges = [];

  /**
   * Nested change; applicable only for conflicts
   * @private
   */
  @observable _childChange;

  @observable _status;
  /**
   * Change's status
   * @type {string}
   */
  @computed
  get status () {
    if (this._parentChanges.length > 0) {
      const applied = this._parentChanges
        .filter(c =>
          c.status === ChangeStatuses.prepared ||
          c.status === ChangeStatuses.building
        ).length === 0;
      return applied ? ChangeStatuses.applied : ChangeStatuses.prepared;
    }
    return this._status;
  }

  /**
   * Updates change status
   * @param value
   */
  set status (value) {
    if (this._parentChanges.length === 0) {
      this._status = value;
    }
  }

  /**
   * Affected lines
   * @type {ConflictedFileLine[]}
   */
  get items () {
    if (!this.conflictedFile || !this.start || !this.end) {
      return [];
    }
    return this.conflictedFile.getLines(
      this.branch,
      new Set([LineStates.omit]),
      this.start,
      this.end
    );
  }

  @computed
  get resolved () {
    return this.status === ChangeStatuses.applied || this.status === ChangeStatuses.discarded;
  }

  constructor (conflictedFile, line, branch) {
    this.conflictedFile = conflictedFile;
    this.start = line;
    this.end = line;
    this.branch = branch;
    this.state = line.state[branch];
    this.conflict = this.state === LineStates.conflictStart;
    switch (this.state) {
      case LineStates.inserted:
        this.type = ModificationType.insertion;
        break;
      case LineStates.removed:
        this.type = ModificationType.deletion;
        break;
      default:
        this.type = ModificationType.conflict;
        break;
    }
    this.status = ChangeStatuses.building;
  }

  /**
   * Returns first affected line
   * @param branch {string}
   * @returns {ConflictedFileLine}
   */
  first (branch) {
    switch (this.type) {
      case ModificationType.edition:
        return this.items
          .find(i =>
            i.state[branch] === LineStates.inserted
          );
      case ModificationType.conflict:
        return this.items
          .find(i =>
            i.state[branch] !== LineStates.conflictStart
          );
      default:
        return this.start;
    }
  }

  /**
   * Returns first non-removed affected line number
   * @param branch {string}
   * @returns {number}
   */
  lineIndex (branch) {
    const lineIndex = this.start.lineNumber[branch];
    const lastLineIndex = this.conflictedFile.getLast(branch).lineNumber[branch];
    switch (this.type) {
      case ModificationType.edition:
      case ModificationType.deletion:
      case ModificationType.conflict:
        return Math.min(lineIndex + 1, lastLineIndex);
      default:
        return lineIndex;
    }
  }

  /**
   * Returns unique identifier
   * @returns {string}
   */
  key () {
    return `${this.start.key}-${this.end.key}`;
  }

  /**
   * Appends line to the change if that line belongs to it.
   * If that line indicates the end of the change, `Change.status` will be updated
   * @param line {ConflictedFileLine}
   */
  appendLine (line) {
    const state = line.state[this.branch];
    const currentState = this.end.state[this.branch];
    if (this.conflict && state === LineStates.conflictEnd) {
      // current change is CONFLICT and appending line indicates
      // CONFLICT END, so we're appending a line and marking change as "prepared"
      // (i.e. end of change/conflict)
      this.end = line;
      this.status = ChangeStatuses.prepared;
    } else if (this.conflict) {
      // current change is CONFLICT and appending line doesn't indicate
      // CONFLICT END, so we're appending a line
      this.end = line;
    } else if (currentState === state) {
      // current change is NOT the conflict and appending line is of the
      // same state as already appended are, so we're appending this line
      this.end = line;
    } else if (
      currentState === LineStates.removed &&
      state === LineStates.inserted
    ) {
      // current change is NOT the conflict and appending line has "INSERTED" state,
      // but already appending lines have "DELETED" one;
      // that means that we're facing such git changes:
      //
      // - REMOVED LINE 1
      // - REMOVED LINE 2
      // + INSERTED LINE
      //
      // such changes we're treating as "EDITION", so we're appending such line.
      // If the following line will also have "INSERTED" state, then it will be appended
      // on the previous <if> clause (because now we have `currentState` == "INSERTED")
      this.end = line;
      this.type = ModificationType.edition;
    } else {
      // current change is NOT the conflict and appending line has different state
      // (and not the special case of "edition").
      // That means that we reached the end of the change
      this.status = ChangeStatuses.prepared;
    }
  }

  /**
   * Applies current change to the MERGED branch
   * @param callback {function} callback to be called after applying the change
   * @return {function(): void} function that reverts changes
   */
  apply (callback) {
    const rollbackActions = [];
    // we're checking:
    // if (this.conflict) - we need to prepare conflict for resolving;
    // if (this.branch !== Merged) - to be sure we're not applying fake "MERGED" conflict;
    // if (this._childChange) - to be sure that all changes related to the same conflict have
    // correct structure: HEAD & REMOTE branches set as parent changes, MERGED (fake) - as a child;
    // if (this.items.length > 1) - safe check that conflict has at least 2 items: start marker &
    // end marker;
    // if (this.status === ChangeStatuses.prepared) - safe check that current change
    // wasn't applied or discarded
    const items = this.items;
    if (
      this.conflict &&
      this.branch !== Merged &&
      this._childChange &&
      items.length > 1 &&
      this.status === ChangeStatuses.prepared
    ) {
      // We need to determine, it it is the first procession (applying, not discarding)
      // of the conflict.
      const firstProcessionOfConflict = !(
        this._childChange._parentChanges
          .find(c => c.status === ChangeStatuses.applied)
      );
      // If it is so (other branch is discarded or not applied yet),
      // we need to "copy" current branch to "merged";
      // Otherwise, fake "MERGED" part was replaced by the first parent
      // change (HEAD or REMOTE). So, we need to add current
      // branch's code to the resulted branch (MERGED)
      const newMergedBranchStart = firstProcessionOfConflict
        ? this.start
        : this.end.previous[Merged];
      const newMergedBranchEnd = this.end;
      const mergedAfterStart = newMergedBranchStart[Merged];
      const mergedBeforeEnd = newMergedBranchEnd.previous[Merged];
      rollbackActions.push(() => {
        newMergedBranchStart[Merged] = mergedAfterStart;
        mergedAfterStart.previous[Merged] = newMergedBranchStart;
        mergedBeforeEnd[Merged] = newMergedBranchEnd;
        newMergedBranchEnd.previous[Merged] = mergedBeforeEnd;
        for (let i = 1; i < items.length - 1; i++) {
          items[i].previous[Merged] = undefined;
          items[i][Merged] = undefined;
        }
      });
      const secondLine = items[1];
      newMergedBranchStart[Merged] = secondLine;
      secondLine.previous[Merged] = newMergedBranchStart;
      // we must take current branch as a new "MERGED" part
      for (let i = 2; i < items.length; i++) {
        const prev = items[i - 1];
        const curr = items[i];
        prev[Merged] = curr;
        curr.previous[Merged] = prev;
      }
    }
    items.forEach(item => {
      const previousState = item.state[Merged];
      const previousText = item.text[Merged];
      item.state[Merged] = item.state[this.branch];
      rollbackActions.push(() => {
        item.state[Merged] = previousState;
        item.text[Merged] = previousText;
      });
    });
    this.conflictedFile.preProcessLines(Merged);
    this.status = ChangeStatuses.applied;
    rollbackActions.push(() => {
      this.status = ChangeStatuses.prepared;
      this.conflictedFile.preProcessLines(Merged);
      this.conflictedFile.notify();
    });
    callback && callback();
    // Returning function that reverts changes
    return () => {
      rollbackActions.forEach(action => action());
    };
  }

  /**
   * Discards current change
   * @param callback {function} callback to be called after discarding the change
   * @return {function(): void} function that reverts changes
   */
  discard (callback) {
    this.status = ChangeStatuses.discarded;
    callback && callback();
    return () => {
      this.status = ChangeStatuses.prepared;
    };
  }

  /**
   * Appends change as a parent for current one; only applicable for "conflicts" -
   * change is parent if it is from HEAD or REMOTE branch, of the same conflict (i.e.
   * has the same start and end lines) and current change is from MERGED branch
   * @param parentChange {Change} parent change candidate
   */
  tryAppendParentChange (parentChange) {
    if (
      this.branch === Merged &&
      parentChange.branch !== Merged &&
      this.type === parentChange.type &&
      this.type === ModificationType.conflict &&
      this.key() === parentChange.key() &&
      this._parentChanges.indexOf(parentChange) === -1
    ) {
      this._parentChanges.push(parentChange);
      parentChange._childChange = this;
    }
  }
}
