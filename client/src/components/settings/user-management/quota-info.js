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
import PropTypes from 'prop-types';
import {Popover} from 'antd';
import classNames from 'classnames';
import {quotaGroupSpendingNames} from '../../billing/quotas/utilities/quota-groups';
import {
  actionNames,
  quotaActionTriggered,
  quotaHasTriggeredActions
} from '../../billing/quotas/utilities/quota-actions';
import quotaTypes, {quotaSubjectName} from '../../billing/quotas/utilities/quota-types';
import periods, {periodNamesAdjective} from '../../billing/quotas/utilities/quota-periods';
import styles from './quota-info.css';

function spending (value) {
  const number = Number(value);
  if (Number.isNaN(number)) {
    return value;
  }
  return Math.round(number * 100) / 100.0;
}

function QuotaInfo ({className, style, quota}) {
  if (!quota) {
    return null;
  }
  const {
    actions = [],
    value,
    period = periods.month,
    quotaGroup,
    type: quotaType,
    subject
  } = quota;
  const {activeAction} = actions.find(action => action.activeAction) || {};
  const {expense = 0} = activeAction || {};
  let description;
  if (expense) {
    description = (
      <div
        key="group"
        className={styles.description}
      >
        {
          quotaType !== quotaTypes.overall && subject && (
            <span>{quotaSubjectName[quotaType]} <b>{subject}</b>:</span>
          )
        }
        <span>{quotaGroupSpendingNames[quotaGroup]}</span>
        <span>{(periodNamesAdjective[period] || period).toLowerCase()}</span>
        <span>expenses</span>
        <b>{spending(expense)}$</b>,
        <span>quota</span>
        <span><b>{spending(value)}$</b>.</span>
      </div>
    );
  } else {
    description = (
      <div
        key="group"
        className={styles.description}
      >
        {
          quotaType !== quotaTypes.overall && subject && (
            <span>{quotaSubjectName[quotaType]} <b>{subject}</b>:</span>
          )
        }
        <span>{quotaGroupSpendingNames[quotaGroup]}</span>
        <span>{(periodNamesAdjective[period] || period).toLowerCase()}</span>
        <span>quota</span>
        <span><b>{spending(value)}$</b>.</span>
      </div>
    );
  }
  return (
    <div
      className={
        classNames(
          className,
          styles.quotaInfo
        )
      }
      style={style}
    >
      {description}
      {
        actions && actions.length && (
          <span className={styles.quotaActionTitle}>
            Actions:
          </span>
        )
      }
      {
        actions.map((action) => {
          return (
            <span
              key={action.id}
              className={
                classNames(
                  styles.quotaAction,
                  {
                    'cp-warning': quotaActionTriggered(action)
                  }
                )
              }
            >
              <span className={styles.threshold}>{action.threshold}%</span>
              {
                (action.actions || []).map(quotaAction => (
                  <span
                    key={quotaAction}
                    className={styles.actionName}
                  >
                    {actionNames[quotaAction] || quotaAction}
                  </span>
                ))
              }
            </span>
          );
        })
      }
    </div>
  );
}

QuotaInfo.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  quota: PropTypes.shape({
    subject: PropTypes.string,
    quotaType: PropTypes.string,
    quotaGroup: PropTypes.string,
    actions: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
    value: PropTypes.number,
    period: PropTypes.string
  })
};

function QuotasInfo (
  {
    className,
    style,
    quotaStyle,
    quotaClassName,
    quotas,
    onlyTriggered
  }
) {
  if (!quotas || !quotas.length) {
    return null;
  }
  const filteredQuotas = (quotas || [])
    .filter(quota => !onlyTriggered || quotaHasTriggeredActions(quota));
  if (filteredQuotas.length === 0) {
    return null;
  }
  const overall = quotas.filter(quota => quota.type === quotaTypes.overall);
  const user = quotas.filter(quota => quota.type === quotaTypes.user);
  const group = quotas.filter(quota => quota.type === quotaTypes.group);
  const billingCenter = quotas.filter(quota => quota.type === quotaTypes.billingCenter);
  return (
    <div
      className={className}
      style={style}
    >
      <div>
        {
          overall.map((quota) => (
            <QuotaInfo
              className={quotaClassName}
              style={quotaStyle}
              key={quota.id}
              quota={quota}
            />
          ))
        }
      </div>
      <div>
        {
          user.map((quota) => (
            <QuotaInfo
              className={quotaClassName}
              style={quotaStyle}
              key={quota.id}
              quota={quota}
            />
          ))
        }
      </div>
      <div>
        {
          group.map((quota) => (
            <QuotaInfo
              className={quotaClassName}
              style={quotaStyle}
              key={quota.id}
              quota={quota}
            />
          ))
        }
      </div>
      <div>
        {
          billingCenter.map((quota) => (
            <QuotaInfo
              className={quotaClassName}
              style={quotaStyle}
              key={quota.id}
              quota={quota}
            />
          ))
        }
      </div>
    </div>
  );
}

QuotasInfo.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  quotaClassName: PropTypes.string,
  quotaStyle: PropTypes.object,
  onlyTriggered: PropTypes.bool
};

function QuotasDisclaimerComponent (
  {
    className,
    style,
    quotas
  }
) {
  if (!quotas || !quotas.length) {
    return null;
  }
  return (
    <Popover
      content={<QuotasInfo onlyTriggered quotas={quotas} />}
    >
      <div
        className={
          classNames(
            'cp-warning',
            className
          )
        }
        style={style}
      >
        <span>Billing quotas exceeded</span>
      </div>
    </Popover>
  );
}

QuotasDisclaimerComponent.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  quotas: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
};

export {
  QuotaInfo,
  QuotasDisclaimerComponent
};
