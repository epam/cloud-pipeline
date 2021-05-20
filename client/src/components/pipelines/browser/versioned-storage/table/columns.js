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
  Icon
} from 'antd';
import DOCUMENT_TYPES from '../document-types';
import UserName from '../../../../special/UserName';
import roleModel from '../../../../../utils/roleModel';
import displaySize from '../../../../../utils/displaySize';
import displayDate from '../../../../../utils/displayDate';
import styles from './table.css';

const ID_PREFIX = 'vs_table-cell';
const FILES = {
  [DOCUMENT_TYPES.blob]: <Icon type="file" />,
  [DOCUMENT_TYPES.tree]: <Icon type="folder" />,
  navback: <Icon type="folder" />
};

const getHtmlId = (id) => {
  if (id) {
    return `${ID_PREFIX}_${id}`;
  }
  return null;
};

const renderActions = (item, index) => {
  if (!item) {
    return null;
  }
  const actions = [];
  if (item.type === DOCUMENT_TYPES.blob) {
    actions.push((
      <Icon
        key="download"
        type="download"
        className={styles.action}
        data-action="download"
        id={getHtmlId(`download-btn${index}`)}
      />
    ));
    if (roleModel.writeAllowed(item)) {
      actions.push((
        <Icon
          key="edit"
          type="edit"
          className={styles.action}
          data-action="edit"
          id={getHtmlId(`edit-btn${index}`)}
        />
      ));
      actions.push((
        <Icon
          key="delete"
          type="delete"
          className={classNames(
            styles.action,
            styles.actionDelete
          )}
          data-action="delete"
          id={getHtmlId(`delete-btn${index}`)}
        />
      ));
    }
  }
  if (item.type === DOCUMENT_TYPES.tree) {
    if (roleModel.writeAllowed(item)) {
      actions.push((
        <Icon
          key="edit"
          type="edit"
          className={styles.action}
          data-action="edit"
          id={getHtmlId(`edit-btn${index}`)}
        />
      ));
      actions.push((
        <Icon
          key="delete"
          type="delete"
          className={classNames(
            styles.action,
            styles.actionDelete
          )}
          data-action="delete"
          id={getHtmlId(`delete-btn${index}`)}
        />
      ));
    }
  }
  return (
    <span
      className={styles.rowActions}
      id={getHtmlId(`actions-container${index}`)}
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
  render: (name = '', record, index) => {
    return (
      <div
        className={styles.cellContent}
        id={getHtmlId(`name${index}`)}
      >
        <span className={styles.fileIcon}>
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
  render: (size, item, index) => (
    <span
      id={getHtmlId(`size${index}`)}
    >
      {item.type === DOCUMENT_TYPES.tree ? '' : displaySize(size, false)}
    </span>
  )
}, {
  title: 'Revision',
  dataIndex: 'commit',
  key: 'commit',
  className: classNames(styles.cell, styles.noWrapCell, styles.revisionCell),
  render: (sha, record, index) => (
    <span
      id={getHtmlId(`commit${index}`)}
    >
      {(sha || '').slice(0, 7)}
    </span>)
}, {
  title: 'Date changed',
  dataIndex: 'committer_date',
  key: 'committer_date',
  className: classNames(styles.cell, styles.noWrapCell, styles.dateCell),
  render: (date, record, index) => (
    <span
      id={getHtmlId(`committer-date${index}`)}
    >
      {displayDate(date)}
    </span>)
}, {
  title: 'Author',
  dataIndex: 'author',
  key: 'author',
  className: classNames(styles.cell, styles.noWrapCell, styles.authorCell),
  render: (author, record, index) => (
    <span id={getHtmlId(`author${index}`)}>
      <UserName userName={author} />
    </span>
  )
}, {
  title: 'Message',
  dataIndex: 'commit_message',
  key: 'commit_message',
  className: classNames(styles.cell, styles.messageCell)
}, {
  title: '',
  key: 'actions',
  className: styles.cell,
  render: (record, _, index) => renderActions(record, index),
  width: 150
}];

export default COLUMNS;
