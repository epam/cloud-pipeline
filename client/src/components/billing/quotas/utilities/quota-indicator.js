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
import {Popover, Tag} from 'antd';
import classNames from 'classnames';

import styles from './quota-indicator.css';

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

const getActivatedActions = (percent, actions = []) => {
  return actions.reduce((res, a) => {
    if (percent >= a.threshold) {
      res.push([[a.threshold], a.actions]);
    }
    return res;
  }, []);
};

const getStatus = (quota) => {
  const {value, actions} = quota;
  const currentValue = quota.currentValue || 720;
  const ratio = currentValue / value;
  const percent = Math.round(ratio * 100);
  const activeActions = getActivatedActions(percent, actions);
  if (ratio > 1) {
    return statuses.exception;
  } else if (activeActions.length) {
    return statuses.warning;
  } else {
    return statuses.active;
  }
};

function QuotaLimitIndicator (props) {
  const {quota} = props;
  const status = getStatus(quota);
  const currentValue = quota.currentValue || 720;
  const ratio = currentValue / quota.value;
  const percent = Math.round(ratio * 100);

  const renderActionsInfo = () => {
    const activeActions = getActivatedActions(percent, quota.actions);
    return (<div>
      {!!activeActions.length && (<h4>Switched on:</h4>)}
      {activeActions.map(([threshold, actions]) => {
        return (<div key={threshold} className={styles.tagsContainer}>
          <span>{threshold}%: </span>
          {actions.map(a => (
            <Tag
              key={a}
              color="orange"
              className={styles.tag}
            >{a}</Tag>))}
          {activeActions.length > 1 && (
            <div className={classNames(
              styles.actionsDivider,
              'cp-divider',
              'bottom'
            )} />)}
        </div>);
      })}
    </div>);
  };

  const renderContent = () => {
    const fullWidth = 150;
    const width = percent > 100 ? 100 : percent;
    return (
      <div>
        <h3>${currentValue}</h3>
        <div
          style={{width: fullWidth}}
          className={classNames(
            'cp-status-bar',
            styles.statusBar
          )}
        >
          <div
            style={{width: `${width}%`}}
            className={classNames(
              styles.barValue,
              statusClassNames[status]
            )}
          >
            {percent}%
          </div>
        </div>
        <h6
          className={styles.quotaValueText}
          style={{width: fullWidth}}
        >
          ${quota.value}
        </h6>
        {renderActionsInfo()}
      </div>
    );
  };

  return (
    <Popover
      content={renderContent()}
      placement="bottom"
    >
      <div className={styles.circleContainer}>
        <svg height="10" width="10">
          <circle
            className={statusClassNames[status]}
            cx="5"
            cy="5"
            r="4"
            strokeWidth={1}
          />
        </svg>
      </div>
    </Popover>
  );
}

export default QuotaLimitIndicator;
