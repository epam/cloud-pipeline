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

import LineStates from '../conflicted-file/line-states';
import Branches, {HeadBranch, RemoteBranch, Merged} from '../conflicted-file/branches';
import Change from './change';
import ChangeStatuses from './statuses';
import ModificationType from './types';
import {findModification, findModificationMarker} from './find';

function processEdition (branch, edition) {
  edition.removed = edition.items.filter(line =>
    line.state[branch] === LineStates.removed
  );
  edition.inserted = edition.items.filter(line =>
    line.state[branch] === LineStates.inserted
  );
}

function processConflict (branch, conflict) {

}

function processModification (conflictedFile, branch, modification) {
  switch (modification.type) {
    case ModificationType.edition:
      processEdition(branch, modification);
      break;
    case ModificationType.conflict:
      processConflict(branch, modification);
      break;
    default:
      break;
  }
}

function getModificationsCountBefore (branch, line, modifications) {
  const lineNumber = line.lineNumber[branch];
  const before = modifications.filter(m => m.lineIndex(branch) < lineNumber);
  return (new Set(before.map(m => m.key()))).size;
}

/**
 * Generates changes array for branch
 * @param conflictedFile {ConflictedFile}
 * @param branch
 * @returns {*[]}
 */
function processBranch (conflictedFile, branch) {
  const skip = new Set([LineStates.omit]);
  const lines = conflictedFile.getLines(branch, skip);
  const result = [];
  let currentModification;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!currentModification && Change.lineIndicatesChange(line, branch)) {
      currentModification = new Change(conflictedFile, line, branch);
    } else if (currentModification) {
      currentModification.appendLine(line);
    }
    if (currentModification && currentModification.status === ChangeStatuses.prepared) {
      result.push(currentModification);
      currentModification = undefined;
    }
  }
  if (currentModification) {
    currentModification.status = ChangeStatuses.prepared;
    result.push(currentModification);
  }
  result.forEach(modification => processModification(conflictedFile, branch, modification));
  return result;
}

function buildChangesBefore (list, branch, modifications = []) {
  const currentModifications = modifications.filter(modification =>
    branch === Merged || modification.branch === branch
  );
  list.getLines(branch, new Set([]))
    .forEach(line => {
      line.changesBefore[branch] = getModificationsCountBefore(
        branch,
        line,
        currentModifications
      );
    });
}

function markLines (list, branch, modifications = []) {
  const currentModifications = modifications.filter(modification =>
    branch === Merged || modification.branch === branch
  );
  list.getLines(branch, new Set([]))
    .forEach(line => {
      const modification = findModification(line, currentModifications, branch);
      line.isChangeMarker[branch] = findModificationMarker(
        line,
        currentModifications,
        branch
      );
      line.change[branch] = modification;
      line.isFirstLineOfChange[branch] = modification &&
        modification.items[0] === line;
      line.isLastLineOfChange[branch] = modification &&
        modification.items[modification.items.length - 1] === line;
    });
}

export default function prepare (conflictedFile) {
  const result = [
    ...processBranch(conflictedFile, HeadBranch),
    ...processBranch(conflictedFile, RemoteBranch),
    ...processBranch(conflictedFile, Merged)
  ];
  Branches.forEach(branch => {
    buildChangesBefore(conflictedFile, branch, result);
    markLines(conflictedFile, branch, result);
  });
  for (let modification of result) {
    const start = modification.items[0];
    modification.changesBefore = {
      [modification.branch]: start
        ? start.changesBefore[modification.branch]
        : 0,
      [Merged]: start
        ? start.changesBefore[Merged]
        : 0
    };
    for (let oModification of result) {
      if (oModification !== modification) {
        oModification.tryAppendParentChange(modification);
        modification.tryAppendParentChange(oModification);
      }
    }
  }
  conflictedFile.changes = result;
  return result;
}
