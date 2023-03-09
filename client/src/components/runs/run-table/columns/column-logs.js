/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Link} from 'react-router';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

const getColumnFilter = () => {};

const getColumn = () => ({
  key: 'actionLog',
  dataIndex: 'id',
  className: styles.runRow,
  render: (id, run) => (
    <RunLoadingPlaceholder run={run} empty>
      <Link
        id={`run-${id}-logs-button`}
        to={`/run/${id}`}
        className={styles.linkAction}
      >
        Log
      </Link>
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
