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
import findModification from './find';

function processEdition (branch, edition) {
  Object.defineProperty(edition, 'removed', {
    get: function () {
      return this.items.filter(line =>
        line.state[branch] === LineStates.removed
      );
    },
    enumerable: true,
    configurable: false
  });
  Object.defineProperty(edition, 'inserted', {
    get: function () {
      return this.items.filter(line =>
        line.state[branch] === LineStates.inserted
      );
    },
    enumerable: true,
    configurable: false
  });
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
  const before = modifications.filter(m => m.start.lineNumber[branch] < lineNumber);
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
      line.change[branch] = findModification(line, currentModifications, branch);
    });
}

export default function prepare (conflictedFile) {
  const result = [
    ...processBranch(conflictedFile, HeadBranch),
    ...processBranch(conflictedFile, RemoteBranch),
    ...processBranch(conflictedFile, Merged)
  ];
  result.sort((a, b) => a.start.lineNumber[a.branch] - b.start.lineNumber[b.branch]);
  Branches.forEach(branch => {
    buildChangesBefore(conflictedFile, branch, result);
    markLines(conflictedFile, branch, result);
  });
  for (let m = 0; m < result.length; m++) {
    const modification = result[m];
    const filtered = result
      .slice(0, m)
      .filter(oModification => oModification.key() !== modification.key());
    const getBeforeCount = (branch) => {
      const allowedBranches = [branch];
      if (branch === Merged) {
        allowedBranches.push(HeadBranch);
        allowedBranches.push(RemoteBranch);
      } else {
        allowedBranches.push(Merged);
      }
      const branches = new Set(allowedBranches);
      const lineNumber = modification.start.lineNumber[branch];
      const before = filtered
        .filter(oModification => branches.has(oModification.branch))
        .filter(oModification =>
          oModification.start.lineNumber[branch] <= lineNumber
        )
        .map(oModification => oModification.key());
      return (new Set(before)).size;
    };
    modification.changesBefore = {
      [modification.branch]: getBeforeCount(modification.branch),
      [Merged]: getBeforeCount(Merged)
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
