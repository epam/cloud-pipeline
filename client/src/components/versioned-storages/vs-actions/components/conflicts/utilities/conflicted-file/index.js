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
import {action, computed, observable} from 'mobx';
import Branches, {
  HeadBranch,
  RemoteBranch,
  Merged
} from './branches';
import LineStates from './line-states';
import ConflictedFileLine from './conflicted-file-line';
import ChangeStatuses from '../changes/statuses';

const ConflictedFileStart = Symbol('conflicted file');

class ConflictedFile {
  @observable changesHash = 0;
  @observable changes = [];
  constructor () {
    this[ConflictedFileStart] = new ConflictedFileLine('', {start: true});
    this[ConflictedFileStart].file = this;
    this.items = [];
    this.operations = [];
    this.operationIndex = -1;
    this.undoOperations = [];
    this.redoOperations = [];
    this.listeners = {};
  }

  addEventListener (event, listener, ...branches) {
    if (!this.listeners.hasOwnProperty(event)) {
      this.listeners[event] = {
        [HeadBranch]: [],
        [Merged]: [],
        [RemoteBranch]: []
      };
    }
    branches.forEach(branch => {
      if (!this.listeners[event][branch].includes(listener)) {
        this.listeners[event][branch].push(listener);
      }
    });
  }

  removeEventListener (event, listener, ...branches) {
    branches.forEach(branch => {
      if (this.listeners[event] && this.listeners[event][branch].includes(listener)) {
        const index = this.listeners[event][branch].indexOf(listener);
        if (index >= 0) {
          this.listeners[event][branch].splice(index, 1);
        }
      }
    });
  }

  fireEvent (event, args, ...branches) {
    if (this.listeners.hasOwnProperty(event)) {
      branches.forEach(branch => {
        this.listeners[event][branch].forEach(callback => callback(args));
      });
    }
  }

  @action
  notify () {
    this.changesHash += 1;
  }

  get canUnDoOperation () {
    return this.operationIndex >= 0 && this.operations.length > this.operationIndex;
  }

  get canReDoOperation () {
    return this.operationIndex + 1 < this.operations.length && this.operations.length > 0;
  }

  get start () {
    return this[ConflictedFileStart];
  }

  @computed
  get resolved () {
    return !(this.changes || []).find(change => !change.resolved);
  }

  applyNonConflictingChanges (...branches) {
    const changes = this.changes
      .filter(change =>
        !change.conflict &&
        branches.indexOf(change.branch) >= 0 &&
        change.status === ChangeStatuses.prepared
      );
    const apply = () => {
      return changes
        .map(change => change.apply(this.notify.bind(this)))
        .reduce((globalRevert, currentRevert) => () => {
          if (globalRevert) {
            globalRevert();
          }
          if (currentRevert) {
            currentRevert();
          }
        }, () => {});
    };
    const revert = apply();
    this.registerOperation({apply, revert});
  }

  registerOperation (operation) {
    const {
      isFinite = true,
      type
    } = operation;
    let insertNewOperation = true;
    if (
      !isFinite &&
      type &&
      this.operations.length > 0 &&
      this.operationIndex < this.operations.length &&
      this.operationIndex >= 0
    ) {
      const current = this.operations[this.operationIndex];
      if (current.type === type && !current.isFinite) {
        insertNewOperation = false;
        this.operations.splice(
          this.operationIndex,
          1,
          {
            ...current,
            apply: (finalOperation = true) => {
              current.apply && current.apply(false);
              operation.apply && operation.apply(finalOperation);
            },
            revert: (finalRevertOperation = true) => {
              operation.revert && operation.revert(false);
              current.revert && current.revert(finalRevertOperation);
            }
          }
        );
      } else {
        current.isFinite = true;
      }
    }
    if (insertNewOperation) {
      this.operations = this.operations.slice(0, this.operationIndex + 1);
      this.operations.push(operation);
      this.operationIndex = this.operations.length - 1;
    }
    this.notify();
  }

  finishCurrentInputOperation () {
    if (this.operationIndex >= 0 && this.operationIndex < this.operations.length) {
      const operation = this.operations[this.operationIndex];
      operation.isFinite = true;
    }
  }

  undoOperation (callback) {
    if (this.canUnDoOperation) {
      this.finishCurrentInputOperation();
      const operation = this.operations[this.operationIndex];
      const {
        revert
      } = operation || {};
      if (typeof revert === 'function') {
        revert();
        callback && callback();
      }
      this.operationIndex -= 1;
      this.notify();
    }
  }

  redoOperation (callback) {
    if (this.canReDoOperation) {
      this.finishCurrentInputOperation();
      this.operationIndex += 1;
      const operation = this.operations[this.operationIndex];
      const {
        apply
      } = operation || {};
      if (typeof apply === 'function') {
        apply();
        callback && callback();
      }
      this.notify();
    }
  }

  acceptBranch (branch) {
    const lines = this.getLines(branch, new Set([]));
    const apply = () => {
      const undos = [];
      undos.push(() => this.buildLineNumbers(Merged));
      for (let c = 0; c < this.changes.length; c++) {
        const change = this.changes[c];
        undos.push(change.discard());
      }
      for (let l = 0; l < lines.length; l++) {
        const line = lines[l];
        const backupMerged = line[Merged];
        const backupPreviousMerged = line[branch] ? line[branch].previous[Merged] : undefined;
        const stateBackup = line.state[Merged];
        undos.push(() => {
          line[Merged] = backupMerged;
          if (line[branch]) {
            line[branch].previous[Merged] = backupPreviousMerged;
          }
          line.state[Merged] = stateBackup;
        });
        line[Merged] = line[branch];
        line.state[Merged] = line.state[branch];
        if (line[branch]) {
          line[branch].previous[Merged] = line;
        }
      }
      this.buildLineNumbers(Merged);
      return undos.reduce((undo, currentUndo) => {
        return () => {
          currentUndo();
          undo();
        };
      }, () => {});
    };
    const revert = apply();
    this.registerOperation({apply, revert});
  }

  getLast (branch, from = undefined) {
    if (!from) {
      return this.getLast(branch, this.start);
    }
    if (!from[branch]) {
      return from;
    }
    return this.getLast(branch, from[branch]);
  }

  move (branch, to) {
    const previous = this.getLast(branch);
    if (previous) {
      previous[branch] = to;
      to.previous[branch] = previous;
    }
  }

  getLastHead () {
    return this.getLast(HeadBranch);
  }

  getLastRemote () {
    return this.getLast(RemoteBranch);
  }

  getLastMerged () {
    return this.getLast(Merged);
  }

  appendLine (line, meta, ...branches) {
    const item = new ConflictedFileLine(line, meta);
    item.file = this;
    this.items.push(item);
    branches.forEach(branch => {
      this.move(branch, item);
    });
  }

  appendConflictMarker (state, meta) {
    const item = new ConflictedFileLine(
      state === LineStates.conflictStart ? '<<<<<<<' : '>>>>>>>',
      meta
    );
    item.file = this;
    item.state[HeadBranch] = state;
    item.state[RemoteBranch] = state;
    item.state[Merged] = state;
    this.items.push(item);
    Branches.forEach(branch => {
      this.move(branch, item);
    });
  }

  appendConflictStart (meta) {
    this.appendConflictMarker(LineStates.conflictStart, meta);
  }

  appendConflictEnd (meta) {
    this.appendConflictMarker(LineStates.conflictEnd, meta);
  }

  insertLineBefore (before, line, meta, ...conflictedBranchDirections) {
    if (before) {
      const item = new ConflictedFileLine(line, meta);
      item.file = this;
      let inserted = false;
      Branches.forEach(branch => {
        let parent = before.previous[branch];
        let child = before;
        if (
          parent &&
          parent.state[branch] === LineStates.conflictEnd
        ) {
          if (conflictedBranchDirections.indexOf(branch) >= 0) {
            child = parent;
            parent = parent.previous[branch];
          } else {
            return;
          }
        }
        inserted = true;
        if (parent) {
          parent[branch] = item;
          item.previous[branch] = parent;
        }
        item[branch] = child;
        child.previous[branch] = item;
      });
      if (inserted) {
        this.items.push(item);
        return item;
      }
    }
    return undefined;
  }

  insertLine (after, text, ...branches) {
    if (after) {
      const newLine = after.copy();
      this.items.push(newLine);
      newLine.file = this;
      Branches.forEach(branch => {
        newLine.text[branch] = text;
        if (branches.indexOf(branch) === -1) {
          newLine.state[branch] = LineStates.omit;
        }
      });
      Branches.forEach(branch => {
        const next = after[branch];
        if (next) {
          next.previous[branch] = newLine;
          newLine[branch] = next;
        }
        newLine.previous[branch] = after;
        after[branch] = newLine;
        this.preProcessLines(branch);
      });
      return newLine;
    }
    return undefined;
  }

  appendLines (lines, meta) {
    for (let line of lines) {
      this.appendLine(line, meta, ...Branches);
    }
  }

  appendHeadLines (lines, meta) {
    for (let line of lines) {
      this.appendLine(line, meta, HeadBranch);
    }
  }

  appendRemoteLines (lines, meta) {
    for (let line of lines) {
      this.appendLine(line, meta, RemoteBranch);
    }
  }

  appendMergedLines (lines, meta) {
    for (let line of lines) {
      this.appendLine(line, meta, Merged);
    }
  }

  static splitText (text, eof) {
    const parts = (text || '')
      .split('\n')
      .map((line, idx, arr) =>
        idx === arr.length - 1
          ? line
          : line.concat('\n')
      );
    if (parts[parts.length - 1] === '' && !eof) {
      parts.pop();
    }
    return parts;
  }

  appendText (text, meta, eof = false) {
    this.appendLines(ConflictedFile.splitText(text, eof), meta);
  }

  appendHeadText (text, meta, eof = false) {
    this.appendHeadLines(ConflictedFile.splitText(text, eof), meta);
  }

  appendRemoteText (text, meta, eof = false) {
    this.appendRemoteLines(ConflictedFile.splitText(text, eof), meta);
  }

  appendMergedText (text, meta, eof = false) {
    this.appendMergedLines(ConflictedFile.splitText(text, eof), meta);
  }

  static getLineAtIndexIgnoreStates = new Set([
    LineStates.omit,
    LineStates.conflictStart,
    LineStates.conflictEnd,
    LineStates.removed
  ]);

  getLineAtIndex (
    branch,
    index,
    ignoreStates = ConflictedFile.getLineAtIndexIgnoreStates,
    from = undefined
  ) {
    if (!from) {
      return this.getLineAtIndex(branch, index, ignoreStates, this.start);
    }
    let correctedIndex = index;
    if (
      ignoreStates.has(from.state[branch])
    ) {
      correctedIndex = index + 1;
    }
    if (correctedIndex === 0) {
      return from;
    }
    if (!from[branch]) {
      return undefined;
    }
    return this.getLineAtIndex(branch, correctedIndex - 1, ignoreStates, from[branch]);
  }

  static getLineAtOriginalIndexIgnoreStates = new Set([
    LineStates.omit,
    LineStates.conflictStart,
    LineStates.conflictEnd,
    LineStates.inserted
  ]);

  getLineAtOriginalIndex (branch, index) {
    return this.getLineAtIndex(
      branch,
      index,
      ConflictedFile.getLineAtOriginalIndexIgnoreStates
    );
  }

  markLine (line, state, omitOthers = true, ...branches) {
    if (line) {
      const restBranches = new Set(Branches);
      branches.forEach(branch => {
        restBranches.delete(branch);
        line.state[branch] = state;
      });
      if (omitOthers) {
        restBranches.forEach(branch => {
          line.state[branch] = LineStates.omit;
        });
      }
    }
  }

  markLineAsInserted (line, ...branches) {
    this.markLine(line, LineStates.inserted, true, ...branches);
  }

  markLineAsRemoved (line, ...branches) {
    this.markLine(line, LineStates.removed, false, ...branches);
  }

  static defaultIgnoreStates = new Set([
    LineStates.omit,
    LineStates.conflictStart,
    LineStates.conflictEnd,
    LineStates.removed
  ]);

  getLines (
    branch,
    ignoreStates = ConflictedFile.defaultIgnoreStates,
    from = undefined,
    to = undefined
  ) {
    if (!from) {
      return this.getLines(branch, ignoreStates, this.start);
    }
    const current = [];
    if (!ignoreStates.has(from.state[branch])) {
      current.push(from);
    }
    if (!from[branch] || from === to) {
      return current;
    }
    return [...current, ...this.getLines(branch, ignoreStates, from[branch], to)];
  }

  getHeadLines () {
    this.correctLineBreaks(HeadBranch);
    return this.getLines(HeadBranch).slice(1);
  }

  getRemoteLines () {
    this.correctLineBreaks(RemoteBranch);
    return this.getLines(RemoteBranch).slice(1);
  }

  getMergedLines () {
    this.correctLineBreaks(Merged);
    return this.getLines(Merged).slice(1);
  }

  getHeadText () {
    return this.getHeadLines().map(o => o.text[HeadBranch]).join('');
  }

  getRemoteText () {
    return this.getRemoteLines().map(o => o.text[RemoteBranch]).join('');
  }

  getMergedText () {
    return this.getMergedLines().map(o => o.text[Merged]).join('');
  }

  getFirstConflictMarker (marker, from = undefined) {
    if (!from) {
      return this.getFirstConflictMarker(marker, this.start);
    }
    if (from.state[Merged] === marker) {
      return from;
    }
    if (!from[Merged]) {
      return undefined;
    }
    return this.getFirstConflictMarker(marker, from[Merged]);
  }

  getItemsBetween (branch, from, end) {
    if (!from) {
      return this.getItemsBetween(branch, this.start, end);
    }
    if (from === end || !from[branch]) {
      return [from];
    }
    return [from, ...this.getItemsBetween(branch, from[branch], end)];
  }

  getFirstConflict (from = undefined) {
    if (!from) {
      return this.getFirstConflict(this.start);
    }
    const startMarker = this.getFirstConflictMarker(LineStates.conflictStart, from);
    if (startMarker) {
      const endMarker = this.getFirstConflictMarker(
        LineStates.conflictEnd,
        startMarker
      );
      if (endMarker) {
        return {
          start: startMarker,
          end: endMarker,
          [HeadBranch]: this.getItemsBetween(HeadBranch, startMarker, endMarker),
          [RemoteBranch]: this.getItemsBetween(RemoteBranch, startMarker, endMarker),
          [Merged]: this.getItemsBetween(Merged, startMarker, endMarker)
        };
      }
    }
    return undefined;
  }

  getConflicts (from = undefined) {
    if (!from) {
      return this.getConflicts(this.start);
    }
    const conflict = this.getFirstConflict(from);
    if (conflict) {
      const {end} = conflict;
      return [conflict, ...this.getConflicts(end)];
    }
    return [];
  }

  preProcessLines (branch) {
    this.buildLineNumbers(branch);
    this.correctLineBreaks(branch);
  }

  correctLineBreaks (branch) {
    const lines = this.getLines(branch);
    for (let i = 0; i < lines.length - 1; i++) {
      const line = lines[i];
      if (
        !line.text[branch] ||
        !line.text[branch].endsWith('\n')
      ) {
        // we need to insert a line break
        line.text[branch] = `${line.text[branch] || ''}\n`;
      }
    }
  }

  buildLineNumbers (branch, from = undefined, last = 0) {
    if (!from) {
      this.buildLineNumbers(branch, this.start, last);
      return;
    }
    const moveNumber = (!from.meta || !from.meta.start) &&
      (
        from.state[branch] === LineStates.inserted ||
        from.state[branch] === LineStates.original
      );
    from.lineNumber[branch] = last + (moveNumber ? 1 : 0);
    if (from[branch]) {
      this.buildLineNumbers(branch, from[branch], last + (moveNumber ? 1 : 0));
    }
  }

  static getModificationsCountBefore (branch, modification, modifications) {
    const lineNumber = modification.lineIndex(branch);
    const before = modifications.filter(m => m.lineIndex(branch) < lineNumber);
    return (new Set(before.map(m => m.key()))).size;
  }

  static processText (items, branch, revert = false) {
    const ignoreStates = new Set([
      LineStates.omit,
      LineStates.conflictStart,
      LineStates.conflictEnd,
      revert
        ? LineStates.inserted
        : LineStates.removed
    ]);
    return (items || [])
      .filter(line => !ignoreStates.has(line.state[branch]))
      .map(line => line.text[branch])
      .join('');
  }

  static findCommon = (o, nextFn, ...candidatesSet) => {
    if (!o) {
      return o;
    }
    const common = candidatesSet
      .map(set => set.has(o))
      .reduce((r, c) => r && c, true);
    if (common) {
      return o;
    }
    const nextCheck = [
      ...(
        new Set(
          Branches
            .map(branch => nextFn(o, branch))
            .filter(Boolean)
        )
      )
    ];
    for (let i = 0; i < nextCheck.length; i++) {
      const candidate = ConflictedFile.findCommon(nextCheck[i], nextFn, ...candidatesSet);
      if (candidate) {
        return candidate;
      }
    }
    return undefined;
  };

  static findClosestParentFor (item, candidates) {
    const candidatesSet = new Set(candidates || []);
    const getParent = (o, branch) => o.previous[branch];
    return ConflictedFile.findCommon(item, getParent, candidatesSet);
  }

  static findClosestChildFor (item, candidates) {
    const candidatesSet = new Set(candidates || []);
    const getChild = (o, branch) => o[branch];
    return ConflictedFile.findCommon(item, getChild, candidatesSet);
  }

  static findCommonParentFor (item, ...branchCandidates) {
    const candidatesSets = branchCandidates.map(array => new Set(array || []));
    const getParent = (o, branch) => o.previous[branch];
    return ConflictedFile.findCommon(item, getParent, ...candidatesSets);
  }

  static findCommonChildFor (item, ...branchCandidates) {
    const candidatesSets = branchCandidates.map(array => new Set(array || []));
    const getChild = (o, branch) => o[branch];
    return ConflictedFile.findCommon(item, getChild, ...candidatesSets);
  }
}

class BinaryConflictedFile extends ConflictedFile {
  @observable acceptedBranch;
  binary = true;

  constructor (info) {
    super();
    this.info = info;
  }

  @computed
  get resolved () {
    return !!this.acceptedBranch && this.acceptedBranch !== Merged;
  }

  acceptBranch (branch) {
    if (branch !== Merged) {
      this.acceptedBranch = branch;
    }
    this.notify();
  }

  getMergedText () {
    return {
      binary: true,
      remote: this.acceptedBranch === RemoteBranch
    };
  }
}

export {BinaryConflictedFile, ConflictedFile};
