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

import {
  getColumnFilter as getDateColumnFilters,
  getColumn as getDateColumn
} from './column-date';
import styles from './run-table-columns.css';

function getColumnFilter (state, setState) {
  return getDateColumnFilters('endDateTo', true, state, setState);
}

const getColumn = () => getDateColumn(
  'Completed',
  'endDate',
  styles.runRowCompleted
);

export {
  getColumn,
  getColumnFilter
};
