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
import {Popover} from 'antd';

import QuotaActions, {getTriggeredActions, quotaHasTriggeredActions} from './quota-actions';
import {quotaGroupSpendingNames} from './quota-groups';
import {periodNames} from './quota-periods';
import styles from './quota-indicator.css'; ;

const statusClassNames = {
  active: 'cp-quota-status-green',
  warning: 'cp-quota-status-yellow',
  exception: 'cp-quota-status-red'
};

const statuses = {
  exception: 'exception',
  active: 'active',
  warning: 'warning'
};

const getStatus = (quota) => {
  const triggered = getTriggeredActions(quota);
  if (triggered.length === 0) {
    return statuses.active;
  }
  if (
    triggered.includes(QuotaActions.block) ||
    triggered.includes(QuotaActions.readModeAndDisableNewJobs) ||
    triggered.includes(QuotaActions.readModeAndStopAllJobs)
  ) {
    return statuses.exception;
  }
  return statuses.warning;
};

const getQuotaExpense = (quota) => {
  const {actions = []} = quota || {};
  const {activeAction} = actions.find(action => action.activeAction) || {};
  const {expense = 0} = activeAction || {};
  const percent = Math.round(expense / quota.value * 100);
  return (
    <div className={styles.expenseContainer}>
      <span>Current expenses:</span>
      <b>{Math.round(expense * 100) / 100}$</b>
      <span>{`(${percent}%)`}</span>
    </div>);
};

function QuotaLimitIndicator (props) {
  const {quota} = props;
  const status = getStatus(quota);
  const renderIndicator = () => (
    <div className={styles.circleContainer}>
      <svg height="10" width="10">
        <circle
          className={
            classNames(
              statusClassNames[status],
              'hide'
            )
          }
          cx="5"
          cy="5"
          r="4"
          strokeWidth={1}
        />
      </svg>
    </div>
  );
  if (quotaHasTriggeredActions(quota)) {
    return (
      <Popover
        content={getQuotaExpense(quota)}
        title={false}
        trigger="hover"
      >
        {renderIndicator()}
      </Popover>
    );
  } else {
    return renderIndicator();
  }
}

export default QuotaLimitIndicator;
