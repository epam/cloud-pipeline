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
import {inject, observer} from 'mobx-react';
import {Popover} from 'antd';
import classNames from 'classnames';
import {quotaGroupSpendingNames} from '../../billing/quotas/utilities/quota-groups';
import {
  actionNames,
  quotaActionTriggered,
  quotaHasTriggeredActions
} from '../../billing/quotas/utilities/quota-actions';
import periods, {periodNames} from '../../billing/quotas/utilities/quota-periods';
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
    quotaGroup
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
        <span>{quotaGroupSpendingNames[quotaGroup]}</span>
        <span>expenses per current</span>
        <span>{(periodNames[period] || period).toLowerCase()}:</span>
        <b>{spending(expense)}$</b>,
        <span>quota:</span>
        <span><b>{spending(value)}$</b>.</span>
      </div>
    );
  } else {
    description = (
      <div
        key="group"
        className={styles.description}
      >
        <span>{quotaGroupSpendingNames[quotaGroup]}</span>
        <span>quota per current</span>
        <span>{(periodNames[period] || period).toLowerCase()}:</span>
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
    quotaGroup: PropTypes.string,
    actions: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
    value: PropTypes.number,
    period: PropTypes.string
  })
};

function SubjectQuotaInfoComponent (
  {
    className,
    style,
    quotaStyle,
    quotaClassName,
    subject,
    type,
    quotas,
    onlyTriggered
  }
) {
  if (!quotas || !quotas.loaded || !subject || !type) {
    return null;
  }
  const subjectQuotas = (quotas.value || [])
    .filter(quota => quota.type === type && quota.subject === subject)
    .filter(quota => !onlyTriggered || quotaHasTriggeredActions(quota));
  if (subjectQuotas.length === 0) {
    return null;
  }
  return (
    <div
      className={className}
      style={style}
    >
      {
        subjectQuotas.map((quota) => (
          <QuotaInfo
            className={quotaClassName}
            style={quotaStyle}
            key={quota.id}
            quota={quota}
          />
        ))
      }
    </div>
  );
}

SubjectQuotaInfoComponent.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  quotaClassName: PropTypes.string,
  quotaStyle: PropTypes.object,
  subject: PropTypes.string,
  type: PropTypes.string,
  onlyTriggered: PropTypes.bool
};

const SubjectQuotaInfo = inject('quotas')(observer(SubjectQuotaInfoComponent));

function QuotasInfo (props) {
  const {
    subject,
    type,
    ...otherProps
  } = props;
  return (
    <SubjectQuotaInfo
      {...otherProps}
      subject={subject}
      type={type}
    />
  );
}

QuotasInfo.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  quotaClassName: PropTypes.string,
  quotaStyle: PropTypes.string,
  subject: PropTypes.string,
  type: PropTypes.string,
  onlyTriggered: PropTypes.bool
};

function QuotasDisclaimerComponent (
  {
    className,
    style,
    type,
    subject,
    quotas
  }
) {
  if (!quotas || !quotas.loaded || !subject || !type) {
    return null;
  }
  const exceededQuotas = (quotas.value || [])
    .filter(quota => quota.type === type && quota.subject === subject)
    .filter(quota => quotaHasTriggeredActions(quota));
  if (exceededQuotas.length > 0) {
    return (
      <Popover
        content={<QuotasInfo subject={subject} type={type} onlyTriggered />}
      >
        <div
          className={classNames('cp-warning', className)}
          style={style}
        >
          <span>Billing quotas exceeded</span>
        </div>
      </Popover>
    );
  }
  return null;
}

QuotasDisclaimerComponent.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  subject: PropTypes.string,
  type: PropTypes.string
};

const QuotasDisclaimer = inject('quotas')(observer(QuotasDisclaimerComponent));

export {
  QuotaInfo,
  SubjectQuotaInfo,
  QuotasDisclaimer
};
