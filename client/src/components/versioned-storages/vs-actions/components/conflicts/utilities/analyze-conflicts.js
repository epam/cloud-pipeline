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

import {BinaryConflictedFile, ConflictedFile} from './conflicted-file';
import {HeadBranch, RemoteBranch, Merged} from './conflicted-file/branches';
import readBlobContents from '../../../../../../utils/read-blob-contents';
import extractRemoteSHA from './extract-remote-sha-from-conflict';
import prepareChanges from './changes/prepare';
import VSFileContent from '../../../../../../models/versioned-storage/file-content';
import VSConflictDiff from '../../../../../../models/versioned-storage/conflict-diff';

function fetchDiff (run, storage, file, options = {}) {
  return new Promise((resolve) => {
    const request = new VSConflictDiff(
      run,
      storage?.id,
      file,
      {
        linesCount: 0,
        ...options
      }
    );
    request
      .fetch()
      .then(() => {
        if (request.loaded) {
          resolve({
            lines: (request.value?.lines || []).slice(),
            binary: request.value?.binary
          });
        } else {
          resolve(undefined);
        }
      })
      .catch(() => resolve(undefined));
  });
}

function parse (contents, mergeInProgress) {
  const linkedList = new ConflictedFile();
  const regExp = /^<<<<<<< [^\n]+\n(.*?)=======\n(.*?)>>>>>>> (.*?)$/gms;
  let result = regExp.exec(contents);
  let previousIndex = 0;
  let conflictId = 0;
  const isEOF = index => contents.length - 1 <= index;
  while (result && result.length === 4) {
    const conflict = {id: conflictId};
    linkedList.appendText(contents.slice(previousIndex, result.index), {}, isEOF(result.index));
    const eof = isEOF(result.index + result[0].length + 1);
    linkedList.appendConflictStart({conflict});
    if (mergeInProgress) {
      linkedList.appendHeadText(result[1], {conflict}, eof);
      linkedList.appendRemoteText(result[2], {conflict}, eof);
    } else {
      linkedList.appendRemoteText(result[1], {conflict}, eof);
      linkedList.appendHeadText(result[2], {conflict}, eof);
    }
    linkedList.appendConflictEnd({conflict});
    previousIndex = result.index + result[0].length + 1;
    result = regExp.exec(contents);
    conflictId += 1;
  }
  if (contents.length > previousIndex) {
    linkedList.appendText(contents.slice(previousIndex), {}, true);
  }
  return linkedList;
}

function prepareConflicts (list) {
  const conflicts = list.getConflicts();
  for (let conflict of conflicts) {
    const head = ConflictedFile.processText(
      conflict[HeadBranch],
      HeadBranch,
      true
    );
    const remote = ConflictedFile.processText(
      conflict[RemoteBranch],
      RemoteBranch,
      true
    );
    const original = head || remote;
    const subList = new ConflictedFile();
    subList.appendMergedText(original);
    const first = subList.getLineAtIndex(Merged, 1, new Set([]));
    const last = subList.getLast(Merged);
    if (first && last) {
      first.changeParent(conflict.start, Merged);
      conflict.end.changeParent(subList.getLast(Merged), Merged);
    }
  }
}

function processDiffs (contents, head, remote, mergeInProgress) {
  const list = parse(contents, mergeInProgress);
  const headRest = (head || []).slice();
  const remoteRest = (remote || []).slice();
  const modificationsAreTheSame = (a, b) => {
    return !!a && !!b && a.origin === b.origin && a.content === b.content;
  };
  const performInsertion = (line, ...branches) => {
    list.markLineAsInserted(line, ...branches);
  };
  const performDeletion = (line, content, ...branches) => {
    const inserted = list.insertLineBefore(line, content, {}, ...branches);
    if (inserted) {
      list.markLineAsRemoved(inserted, ...branches);
    }
  };
  const performModification = (line, modification, ...branches) => {
    if (modification.origin === '+') {
      performInsertion(line, ...branches);
    } else {
      performDeletion(line, modification.content, ...branches);
    }
  };
  while (headRest.length > 0 || remoteRest.length > 0) {
    const headModification = headRest[0];
    const remoteModification = remoteRest[0];
    let headLine, remoteLine;
    if (headModification) {
      if (headModification.origin === '+') {
        headLine = list.getLineAtIndex(HeadBranch, headModification.new_lineno);
      } else if (headModification.origin === '-') {
        headLine = list.getLineAtOriginalIndex(
          HeadBranch,
          headModification.old_lineno
        );
      }
    }
    if (remoteModification) {
      if (remoteModification.origin === '+') {
        remoteLine = list.getLineAtIndex(RemoteBranch, remoteModification.new_lineno);
      } else if (remoteModification.origin === '-') {
        remoteLine = list.getLineAtOriginalIndex(
          RemoteBranch,
          remoteModification.old_lineno
        );
      }
    }
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
        performModification(
          headLine,
          headModification,
          HeadBranch
        );
        headRest.splice(0, 1);
      }
      if (performRemote) {
        performModification(remoteLine, remoteModification, RemoteBranch);
        remoteRest.splice(0, 1);
      }
      if (!performHead && !performRemote) {
        headRest.splice(0, 1);
        remoteRest.splice(0, 1);
      }
    }
  }
  prepareConflicts(list);
  list.preProcessLines(HeadBranch);
  list.preProcessLines(RemoteBranch);
  list.preProcessLines(Merged);
  prepareChanges(list);
  return list;
}

export default function analyzeConflicts (run, storage, mergeInProgress, file, fileInfo) {
  if (!run || !storage || !file) {
    return Promise.resolve({});
  }
  if (fileInfo && fileInfo.binary) {
    return Promise.resolve(new BinaryConflictedFile(fileInfo));
  }
  return new Promise((resolve) => {
    const request = new VSFileContent(run, storage.id, file);
    request
      .fetch()
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        } else {
          return readBlobContents(request.response);
        }
      })
      .then((contents) => {
        const remoteSHA = mergeInProgress ? extractRemoteSHA(contents) : undefined;
        const promises = [];
        if (mergeInProgress) {
          // merge conflict
          // head:
          promises.push(
            fetchDiff(run, storage, file, {mergeInProgress})
          );
          // remote:
          promises.push(
            remoteSHA
              ? fetchDiff(run, storage, file, {revision: remoteSHA, mergeInProgress})
              : Promise.resolve(undefined)
          );
        } else {
          // head:
          promises.push(fetchDiff(run, storage, file, {mergeInProgress}));
          // remote:
          promises.push(fetchDiff(run, storage, file, {mergeInProgress, remote: true}));
        }
        return Promise.all([...promises, Promise.resolve(contents)]);
      })
      .then((payloads) => {
        const [
          head,
          remote,
          contents
        ] = payloads;
        if (head?.binary || remote?.binary) {
          throw new Error(`Binary file`);
        }
        const headLines = head?.lines;
        const remoteLines = remote?.lines;
        resolve(processDiffs(contents, headLines, remoteLines, mergeInProgress));
      })
      .catch(() => resolve(new BinaryConflictedFile()));
  });
}
