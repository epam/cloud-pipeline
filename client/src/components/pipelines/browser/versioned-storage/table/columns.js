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
import {
  Button,
  Icon
} from 'antd';
import DOCUMENT_TYPES from '../document-types';
import UserName from '../../../../special/UserName';
import roleModel from '../../../../../utils/roleModel';
import displaySize from '../../../../../utils/displaySize';
import displayDate from '../../../../../utils/displayDate';
import styles from './table.css';

const FILES = {
  [DOCUMENT_TYPES.blob]: <Icon type="file" />,
  [DOCUMENT_TYPES.tree]: <Icon type="folder" />,
  navback: <Icon type="folder" />
};

const renderActions = (item) => {
  if (!item) {
    return null;
  }
  const actions = [];
  if (item.type === DOCUMENT_TYPES.blob) {
    actions.push((
      <Button
        key="download"
        className={styles.action}
        data-action="download"
        size="small"
      >
        <Icon type="download" />
      </Button>
    ));
    if (roleModel.writeAllowed(item)) {
      actions.push((
        <Button
          key="edit"
          className={styles.action}
          data-action="edit"
          size="small"
        >
          <Icon type="edit" />
        </Button>
      ));
      actions.push((
        <Button
          key="delete"
          type="danger"
          className={styles.action}
          data-action="delete"
          size="small"
        >
          <Icon type="delete" />
        </Button>
      ));
    }
  }
  if (item.type === DOCUMENT_TYPES.tree) {
    if (roleModel.writeAllowed(item)) {
      actions.push((
        <Button
          key="edit"
          className={styles.action}
          data-action="edit"
          size="small"
        >
          <Icon type="edit" />
        </Button>
      ));
      actions.push((
        <Button
          key="delete"
          type="danger"
          className={styles.action}
          data-action="delete"
          size="small"
        >
          <Icon type="delete" />
        </Button>
      ));
    }
  }
  return (
    <span
      className={styles.rowActions}
    >
      {actions}
    </span>
  );
};

const COLUMNS = [{
  title: 'Name',
  dataIndex: 'name',
  key: 'name',
  className: classNames(styles.cell, styles.nameCell),
  width: 200,
  render: (name = '', record) => {
    return (
      <div className={styles.cellContent}>
        <span className={classNames(styles.fileIcon, 'cp-primary', 'cp-icon-larger')}>
          {FILES[record.type]}
        </span>
        <span>{name}</span>
      </div>
    );
  }
}, {
  title: 'Size',
  dataIndex: 'size',
  key: 'size',
  className: classNames(styles.cell, styles.noWrapCell, styles.sizeCell),
  render: (size, item) => item.type === DOCUMENT_TYPES.tree
    ? undefined
    : displaySize(size, false)
}, {
  title: 'Revision',
  dataIndex: 'commit',
  key: 'commit',
  className: classNames(styles.cell, styles.noWrapCell, styles.revisionCell),
  render: sha => (sha || '').slice(0, 7)
}, {
  title: 'Date changed',
  dataIndex: 'committer_date',
  key: 'committer_date',
  className: classNames(styles.cell, styles.noWrapCell, styles.dateCell),
  render: date => displayDate(date)
}, {
  title: 'Author',
  dataIndex: 'author',
  key: 'author',
  className: classNames(styles.cell, styles.noWrapCell, styles.authorCell),
  render: author => <UserName userName={author} />
}, {
  title: 'Message',
  dataIndex: 'commit_message',
  key: 'commit_message',
  className: classNames(styles.cell, styles.messageCell)
}, {
  title: '',
  key: 'actions',
  className: styles.cell,
  render: (record) => renderActions(record),
  width: 150
}];

export default COLUMNS;
