/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import styles from './typing-indicator.module.css';

const Dot = ({className, style}) => (
  <svg className={className} style={style} height="7" width="7">
    <circle cx="3.5" cy="3.5" r="3.5" strokeWidth={1} />
  </svg>
);

export default function TypingIndicator({className}) {
  return (
    <div className={classNames(styles.indicator, className)}>
      <Dot className={classNames(styles.dot, styles.delay1)} />
      <Dot className={classNames(styles.dot, styles.delay2)} />
      <Dot className={classNames(styles.dot, styles.delay3)} />
    </div>
  );
}
