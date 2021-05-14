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

import classNames from 'classnames';
import styles from '../../../conflicts.css';
import ChangeStatuses from '../../../utilities/changes/statuses';

export default function getClassNameForChange (change, options) {
  const {type, status} = change || {};
  const {isFirst, isLast, hidden} = options || {};
  return classNames(
    styles.line,
    {
      [styles.modification]: !!type,
      [styles.firstLine]: !!type && isFirst,
      [styles.lastLine]: !!type && isLast,
      [styles.applied]: status === ChangeStatuses.applied,
      [styles.discarded]: status === ChangeStatuses.discarded,
      [styles.hidden]: hidden
    }
  );
}
