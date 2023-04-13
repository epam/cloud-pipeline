/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import classNames from 'classnames';
import styles from './label.css';

export const STATUS_COLORS = {
  new: 'cp-primary border',
  opened: 'cp-primary border',
  open: 'cp-primary border',
  closed: 'cp-success border',
  close: 'cp-success border',
  inprogress: 'cp-warning border',
  'in-progress': 'cp-warning border',
  'in progress': 'cp-warning border'
};

export default function Label ({label = '', style = {}, className}) {
  if (!label) {
    return null;
  }
  return (
    <div
      className={
        classNames(
          'cp-bordered',
          STATUS_COLORS[label.toLowerCase()],
          styles.label,
          className
        )
      }
      style={style}
    >
      {label}
    </div>
  );
};
