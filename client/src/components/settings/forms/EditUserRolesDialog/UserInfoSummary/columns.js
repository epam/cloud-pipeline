/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {
  numberFormatter,
  costTickFormatter
} from '../../../../../components/billing/reports/utilities';
import styles from './UserInfoSummary.css';

const TABLE_MODES = {
  computed: 'computed',
  storages: 'storages'
};

const GROUPING = {
  TOOL: 'Tool',
  PIPELINE: 'Pipeline',
  STORAGE: 'Storage'
};

const TABLE_CATEGORIES = {
  [TABLE_MODES.computed]: [GROUPING.TOOL, GROUPING.PIPELINE],
  [TABLE_MODES.storages]: [GROUPING.STORAGE]
};

const COLUMNS = {
  [TABLE_MODES.computed]: [{
    key: 'name',
    dataIndex: 'name',
    title: 'Name',
    className: styles.tableCell
  }, {
    key: 'type',
    dataIndex: 'type',
    title: 'Type',
    className: styles.tableCell
  }, {
    key: 'usage',
    dataIndex: 'usage',
    title: 'Usage (hours)',
    className: styles.tableCell,
    render: (value) => value ? numberFormatter(value) : null
  }, {
    key: 'runsCount',
    dataIndex: 'runsCount',
    title: 'Runs count',
    className: styles.tableCell
  }, {
    key: 'cost',
    dataIndex: 'value',
    title: 'Cost',
    className: styles.tableCell,
    render: (value) => value ? costTickFormatter(value) : null
  }],
  [TABLE_MODES.storages]: [{
    key: 'name',
    dataIndex: 'name',
    title: 'Name',
    className: styles.tableCell
  }, {
    key: 'type',
    dataIndex: 'type',
    title: 'Type',
    className: styles.tableCell
  }, {
    key: 'storageType',
    title: 'Storage type',
    dataIndex: 'storageType'
  }, {
    key: 'cost',
    title: 'Cost',
    dataIndex: 'value',
    className: styles.tableCell,
    render: (value) => value ? costTickFormatter(value) : null
  }, {
    key: 'volume',
    title: 'Avg. Vol. (GB)',
    dataIndex: 'usage',
    className: styles.tableCell,
    render: (value) => value ? numberFormatter(value) : null
  }, {
    key: 'volume current',
    title: 'Cur. Vol. (GB)',
    dataIndex: 'usageLast',
    className: styles.tableCell,
    render: (value) => value ? numberFormatter(value) : null
  }]
};

export {
  COLUMNS,
  TABLE_CATEGORIES,
  TABLE_MODES,
  GROUPING
};
