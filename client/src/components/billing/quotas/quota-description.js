/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Tooltip} from 'antd';
import * as billing from '../../../models/billing';
import styles from './quotas.css';

function plural (count, noun) {
  return `${noun}${count > 1 ? 's' : ''}`;
}

function blockEvent (e) {
  e && e.stopPropagation();
  e && e.preventDefault();
  return false;
}

function QuotaDescription ({quota}) {
  if (!quota) {
    return null;
  }
  const renderAction = (action, index) => {
    const {value} = quota;
    const {action: name, threshold} = action;
    if (!isNaN(value) && +value > 0) {
      return (
        <div key={index} className={styles.action}>
          <span className={styles.threshold}>
            ${Math.round(+value * (+threshold)) / 100.0} ({threshold}%):
          </span>
          <span className={styles.actionName}>{billing.quotas.getRuleName(name)}</span>
        </div>
      );
    }
    return null;
  };
  const {actions, value} = quota;
  return (
    <div className={styles.quotaDescriptionContainer}>
      <div className={styles.quotaDescription}>
        <div className={styles.valueContainer}>
          <span className={styles.value}>
            ${value}
          </span>
          <span className={styles.period}>
            per month.
          </span>
        </div>
        <div className={styles.actionsContainer}>
          {
            actions && actions.length > 0 &&
            (
              <Tooltip
                placement="right"
                title={(
                  <div className={styles.tooltip}>
                    {actions.map(renderAction)}
                  </div>
                )}
              >
                <a onClick={blockEvent}>
                  {actions.length} {plural(actions.length, 'action')}
                </a>
              </Tooltip>
            )
          }
        </div>
      </div>
    </div>
  );
}

export default QuotaDescription;
