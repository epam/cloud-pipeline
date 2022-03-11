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

import React from 'react';
import classNames from 'classnames';
import {observer} from 'mobx-react';
import {
  Button,
  Icon
} from 'antd';
import ChangeStatuses from '../utilities/changes/statuses';
import {HeadBranch, RemoteBranch} from '../utilities/conflicted-file/branches';
import styles from './resolve-changes.css';

const plural = (strings, ...counts) => {
  const [before, ...after] = strings;
  const proceed = (stringsIgnored, count = '', words = '') => {
    // eslint-disable-next-line
    const e = /^[\s]?([^s.,!?()\[\];\-+]*)(.*)$/.exec(words);
    if (e && e.length > 2) {
      const aWord = e[1];
      const rest = e[2];
      const result = `${aWord}${aWord.length && Number(count) > 1 ? 's' : ''}`;
      return `${count} ${result}${rest}`;
    }
    return '';
  };
  let pluralString = before;
  for (let i = 0; i < after.length; i++) {
    pluralString = pluralString.concat(proceed`${counts[i]}${after[i]}`);
  }
  return pluralString;
};

function hasNonConflictingChanges (changes, ...branch) {
  return changes
    .some(change =>
      branch.indexOf(change.branch) >= 0 &&
      !change.conflict &&
      change.status === ChangeStatuses.prepared
    );
}

function Divider () {
  return (
    <div className={classNames('cp-divider', 'vertical')}>
      {'\u00A0'}
    </div>
  );
}

function ResolveChanges (
  {
    className,
    conflictedFile
  }
) {
  if (!conflictedFile) {
    return null;
  }
  const {changes = []} = conflictedFile;
  const notProcessedChanges = changes
    .filter(change => change.status === ChangeStatuses.prepared);
  const changesCount = (
    new Set(
      notProcessedChanges
        .filter(change => !change.conflict)
        .map(change => change.key())
    )
  ).size;
  const conflictsCount = (
    new Set(
      notProcessedChanges
        .filter(change => change.conflict)
        .map(change => change.key())
    )
  ).size;
  const canApplyYours = hasNonConflictingChanges(changes, HeadBranch);
  const canApplyTheirs = hasNonConflictingChanges(changes, RemoteBranch);
  const canApplyAll = canApplyYours && canApplyTheirs;
  const applyNonConflictingChanges = (...branches) => {
    conflictedFile.applyNonConflictingChanges(...branches);
  };
  return (
    <div
      data-conflicted-file-changes-hash={conflictedFile.changesHash}
      className={
        classNames(
          styles.container,
          className
        )
      }
    >
      <Button
        id="conflict-changes-undo-operation"
        size="small"
        disabled={!conflictedFile.canUnDoOperation}
        className={styles.button}
        onClick={() => conflictedFile.undoOperation()}
      >
        <Icon type="rollback" />
      </Button>
      <Button
        size="small"
        id="conflict-changes-redo-operation"
        disabled={!conflictedFile.canReDoOperation}
        className={styles.button}
        onClick={() => conflictedFile.redoOperation()}
      >
        <Icon type="rollback" style={{transform: 'scaleX(-1)'}} />
      </Button>
      <Divider />
      <span>
        Apply non-conflicting changes:
      </span>
      <Button
        size="small"
        id="conflict-changes-apply-non-conflicting-yours"
        className={styles.button}
        disabled={!canApplyYours}
        onClick={() => applyNonConflictingChanges(HeadBranch)}
      >
        Yours<Icon type="arrow-right" />
      </Button>
      <Button
        size="small"
        id="conflict-changes-apply-non-conflicting-all"
        className={styles.button}
        disabled={!canApplyAll}
        onClick={() => applyNonConflictingChanges(HeadBranch, RemoteBranch)}
      >
        <Icon type="arrow-right" />
        All
        <Icon type="arrow-left" />
      </Button>
      <Button
        size="small"
        id="conflict-changes-apply-non-conflicting-theirs"
        className={styles.button}
        disabled={!canApplyTheirs}
        onClick={() => applyNonConflictingChanges(RemoteBranch)}
      >
        <Icon type="arrow-left" />Theirs
      </Button>
      <Divider />
      <Button
        id="conflict-changes-accept-yours"
        className={styles.button}
        disabled={conflictedFile.resolved}
        onClick={() => conflictedFile.acceptBranch(HeadBranch)}
        type="primary"
        size="small"
      >
        Accept Yours
      </Button>
      <Button
        id="conflict-changes-accept-theirs"
        className={styles.button}
        disabled={conflictedFile.resolved}
        onClick={() => conflictedFile.acceptBranch(RemoteBranch)}
        type="primary"
        size="small"
      >
        Accept Theirs
      </Button>
      <span
        id="conflict-changes-summary"
        className={styles.total}
      >
        {
          [
            changesCount > 0 && plural`${changesCount} change`,
            conflictsCount > 0 && plural`${conflictsCount} conflict`
          ]
            .filter(Boolean).join('. ')
        }
        {
          changesCount === 0 &&
          conflictsCount === 0 &&
          'All conflicts have been resolved'
        }
      </span>
    </div>
  );
}

export default observer(ResolveChanges);
