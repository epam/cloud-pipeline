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

import ConflictedFile from './conflicted-file';
import LineStates from './conflicted-file/line-states';
import {HeadBranch, RemoteBranch, Merged} from './conflicted-file/branches';
import readBlobContents from './read-blob-contents';
import extractRemoteSHA from './extract-remote-sha-from-conflict';
import prepareChanges from './changes/prepare';
import VSFileContent from '../../../../../../models/versioned-storage/file-content';
import VSConflictDiff from '../../../../../../models/versioned-storage/conflict-diff';

function tempLog (...o) {
  // console.log(...o);
}

function tempWarn (...o) {
  // console.warn(...o);
}

function fetchDiff (run, storage, file, revision, mergeInProgress) {
  return new Promise((resolve) => {
    const request = new VSConflictDiff(
      run,
      storage?.id,
      file,
      revision,
      {
        linesCount: 0,
        mergeInProgress
      }
    );
    request
      .fetch()
      .then(() => {
        if (request.loaded) {
          resolve((request.value?.lines || []).slice());
        } else {
          resolve(undefined);
        }
      })
      .catch(() => resolve(undefined));
  });
}

function parse (contents) {
  tempLog('CONTENTS:');
  tempLog(contents.replace(/\n/g, '↵'));
  const linkedList = new ConflictedFile();
  const regExp = /^<<<<<<< [^\n]+\n(.*?)=======\n(.*?)>>>>>>> (.*?)$/gms;
  let result = regExp.exec(contents);
  let previousIndex = 0;
  let conflictId = 0;
  while (result && result.length === 4) {
    const conflict = {id: conflictId};
    linkedList.appendText(contents.slice(previousIndex, result.index), {});
    linkedList.appendConflictStart({conflict});
    linkedList.appendHeadText(result[1], {conflict});
    linkedList.appendRemoteText(result[2], {conflict});
    linkedList.appendConflictEnd({conflict});
    previousIndex = result.index + result[0].length + 1;
    result = regExp.exec(contents);
    conflictId += 1;
  }
  linkedList.appendText(contents.slice(previousIndex), {});
  return linkedList;
}

function prepareConflicts (list) {
  const conflicts = list.getConflicts();
  for (let conflict of conflicts) {
    const original = ConflictedFile.processText(
      conflict[RemoteBranch],
      RemoteBranch,
      true
    );
    const subList = new ConflictedFile();
    subList.appendMergedText(original);
    const first = subList.getLineAtIndex(Merged, 1, new Set([]));
    first.changeParent(conflict.start, Merged);
    conflict.end.changeParent(subList.getLast(Merged), Merged);
    tempLog(conflict);
  }
}

function processDiffs (contents, head, remote) {
  const list = parse(contents);
  tempLog(list);
  tempLog(head, remote);
  const headRest = (head || []).slice();
  const remoteRest = (remote || []).slice();
  const printLineNo = no => no > 0 ? no : '.';
  const printModification = modification => [
    `${printLineNo(modification.old_lineno)} -> ${printLineNo(modification.new_lineno)}`,
    modification.origin === '+' ? 'INS' : 'DEL',
    `"${modification.content.replace(/\n/g, '↵')}"`
  ].join(' ');
  const modificationsAreTheSame = (a, b) => {
    return !!a && !!b && a.origin === b.origin && a.content === b.content;
  };
  const performInsertion = (line, ...branches) => {
    list.markLineAsInserted(line, ...branches);
  };
  const performDeletion = (line, content, ...branches) => {
    const inserted = list.insertLineBefore(line, content, {}, ...branches);
    if (inserted) {
      tempLog('INSERTED REMOVED LINE', branches, inserted);
      list.markLineAsRemoved(inserted, ...branches);
    } else {
      tempWarn('SKIPPED (possible conflict');
    }
  };
  const performModification = (line, modification, ...branches) => {
    if (modification.origin === '+') {
      tempLog('PERFORMING INSERTION', printModification(modification));
      if (modification.content !== line.line) {
        tempWarn('SOMETHING WENT WRONG: not equal lines');
      }
      performInsertion(line, ...branches);
    } else {
      tempLog('PERFORMING DELETION', printModification(modification));
      performDeletion(line, modification.content, ...branches);
    }
  };
  while (headRest.length > 0 || remoteRest.length > 0) {
    const headModification = headRest[0];
    const remoteModification = remoteRest[0];
    let headLine, remoteLine;
    if (headModification) {
      tempLog(
        'HEAD:  ',
        printModification(headModification)
      );
      if (headModification.origin === '+') {
        headLine = list.getLineAtIndex(HeadBranch, headModification.new_lineno);
      } else if (headModification.origin === '-') {
        headLine = list.getLineAtOriginalIndex(
          HeadBranch,
          headModification.old_lineno
        );
      }
    } else {
      tempLog('HEAD:   missing');
    }
    if (remoteModification) {
      tempLog(
        'REMOTE:',
        printModification(remoteModification)
      );
      if (remoteModification.origin === '+') {
        remoteLine = list.getLineAtIndex(RemoteBranch, remoteModification.new_lineno);
      } else if (remoteModification.origin === '-') {
        remoteLine = list.getLineAtOriginalIndex(
          RemoteBranch,
          remoteModification.old_lineno
        );
      }
    } else {
      tempLog('REMOTE: missing');
    }
    tempLog('HEAD LINE:', headLine);
    tempLog('REMOTE LINE:', remoteLine);
    let sameModifications = false;
    let performHead = false;
    let performRemote = false;
    if (remoteLine && headLine) {
      sameModifications = remoteLine === headLine &&
        modificationsAreTheSame(headModification, remoteModification);
      const headIsParent = headLine.isParentFor(remoteLine);
      const remoteIsParent = headIsParent ? false : remoteLine.isParentFor(headLine);
      if (!headIsParent && !remoteIsParent) {
        // we're at conflicting branches; both modifications can be performed
        // (as they will be skipped)
        performHead = true;
        performRemote = true;
      } else {
        performHead = headIsParent;
        performRemote = remoteIsParent;
      }
    } else {
      performHead = !!headLine;
      performRemote = !!remoteLine;
    }
    if (sameModifications) {
      tempLog('BOTH will be performed (the same)');
      performModification(
        remoteLine,
        remoteModification,
        HeadBranch,
        RemoteBranch
      );
      headRest.splice(0, 1);
      remoteRest.splice(0, 1);
    } else {
      if (performHead) {
        tempLog('HEAD will be performed');
        performModification(
          headLine,
          headModification,
          HeadBranch
        );
        headRest.splice(0, 1);
      }
      if (performRemote) {
        tempLog('REMOTE will be performed');
        performModification(remoteLine, remoteModification, RemoteBranch);
        remoteRest.splice(0, 1);
      }
      if (!performHead && !performRemote) {
        tempWarn('NOTHING will be performed. Something wrong');
        headRest.splice(0, 1);
        remoteRest.splice(0, 1);
      }
    }
    tempLog('RESULT:');
    tempLog('HEAD:');
    tempLog(list.getHeadText());
    tempLog('REMOTE:');
    tempLog(list.getRemoteText());
    const skip = new Set([LineStates.omit]);
    const str = (o, length) => (new Array(Math.max(length, o.length)))
      .fill(' ')
      .join('')
      .slice(0, Math.max(length, o.length) - o.length)
      .concat(o);
    tempLog('HEAD:');
    const headFinal = list
      .getLines(HeadBranch, skip)
      .slice(1)
      .map(line =>
        `${str(line.state[HeadBranch], 10)} ${line.text[HeadBranch].replace(/\n/g, '↵')}`
      )
      .join('\n');
    tempLog(headFinal);
    tempLog('REMOTE:');
    const remoteFinal = list
      .getLines(RemoteBranch, skip)
      .slice(1)
      .map(line =>
        `${str(line.state[RemoteBranch], 10)} ${line.text[RemoteBranch].replace(/\n/g, '↵')}`
      )
      .join('\n');
    tempLog(remoteFinal);
    tempLog('=================================');
    tempLog('');
  }
  prepareConflicts(list);
  list.buildLineNumbers(HeadBranch);
  list.buildLineNumbers(RemoteBranch);
  list.buildLineNumbers(Merged);
  prepareChanges(list);
  console.log('FINAL:');
  const skip = new Set([LineStates.omit]);
  const str = (o, length) => (new Array(Math.max(length, o.toString().length)))
    .fill(' ')
    .join('')
    .slice(0, Math.max(length, o.toString().length) - o.toString().length)
    .concat(o.toString());
  console.log('HEAD:');
  const headFinal = list
    .getLines(HeadBranch, skip)
    .slice(1)
    .map(line =>
      `${str(line.key, 4)} ${str(line.lineNumber[HeadBranch], 4)} ${str(line.state[HeadBranch], 10)} ${line.text[HeadBranch].replace(/\n/g, '↵')}`
    )
    .join('\n');
  console.log(headFinal);
  console.log('REMOTE:');
  const remoteFinal = list
    .getLines(RemoteBranch, skip)
    .slice(1)
    .map(line =>
      `${str(line.key, 4)} ${str(line.lineNumber[RemoteBranch], 4)} ${str(line.state[RemoteBranch], 10)} ${line.text[RemoteBranch].replace(/\n/g, '↵')}`
    )
    .join('\n');
  console.log(remoteFinal);
  console.log('RESULTED:');
  const resultFinal = list
    .getLines(Merged, skip)
    .slice(1)
    .map(line =>
      `${str(line.key, 4)} ${str(line.lineNumber[Merged], 4)} ${str(line.state[Merged], 10)} ${line.text[Merged].replace(/\n/g, '↵')}`
    )
    .join('\n');
  console.log(resultFinal);
  return list;
}

export default function analyzeConflicts (run, storage, mergeInProgress, file) {
  if (!run || !storage || !file) {
    return Promise.resolve({});
  }
  return new Promise((resolve, reject) => {
    tempLog('analyze conflicts', run, storage, file);
    const request = new VSFileContent(run, storage.id, file);
    request
      .fetch()
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        } else {
          readBlobContents(request.response)
            .then((contents) => {
              const remoteSHA = mergeInProgress ? extractRemoteSHA(contents) : undefined;
              const promises = [];
              if (mergeInProgress) {
                // merge conflict
                // head:
                promises.push(
                  fetchDiff(run, storage, file, undefined, mergeInProgress)
                );
                // remote:
                promises.push(
                  remoteSHA
                    ? fetchDiff(run, storage, file, remoteSHA, mergeInProgress)
                    : Promise.resolve(undefined)
                );
              } else {
                // todo: stash conflict
                // head:
                // promises.push(fetchDiff(run, storage, file, undefined, mergeInProgress));
                promises.push(Promise.resolve(undefined));
                // remote:
                promises.push(Promise.resolve(undefined));
              }
              Promise.all(promises)
                .then((payloads) => {
                  const [
                    head,
                    remote
                  ] = payloads;
                  if (head) {
                    head.key = 'HEAD';
                  }
                  if (remote) {
                    remote.key = remoteSHA;
                  }
                  resolve(processDiffs(contents, head, remote));
                });
            });
        }
      })
      .catch(reject);
  });
}
