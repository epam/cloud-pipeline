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
import QuotaTarget from './quota-target';
import {QuotaAction} from './quota-actions';
import {periodNames} from './quota-periods';
import {numberFormatter} from '../../reports/utilities';
import styles from './quota-description.css';

function sortActionsByThreshold (a, b) {
  const {threshold: aThreshold} = a;
  const {threshold: bThreshold} = b;
  return +aThreshold - +bThreshold;
}

function QuotaDescription (
  {
    className,
    onClick,
    quota,
    roles,
    style
  }
) {
  if (!quota) {
    return null;
  }
  const {
    actions = [],
    value,
    period = 'MONTH'
  } = quota;
  const quotaValue = (
    <span className={styles.quotaValue}>
      {numberFormatter(value)}$ per {(periodNames[period] || period).toLowerCase()}
    </span>
  );
  return (
    <div
      className={
        classNames(
          styles.container,
          className
        )
      }
      style={style}
      onClick={onClick}
    >
      <div className={styles.description}>
        <div>
          <QuotaTarget
            addonAfter=":"
            quota={quota}
            roles={roles}
          />
          {quotaValue}
        </div>
      </div>
      <div className={styles.actions}>
        {
          actions
            .sort(sortActionsByThreshold)
            .map((action, index) => (
              <QuotaAction
                className={styles.action}
                key={`action-${index}`}
                action={action}
              />
            ))
        }
      </div>
    </div>
  );
}

export default QuotaDescription;
