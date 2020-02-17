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
import {Icon} from 'antd';
import styles from './billing-table.css';
import {observer} from 'mobx-react';

function getPreviousValues (values = []) {
  const previousValues = values.filter(v => v.previous && v.value);
  const from = previousValues.shift();
  const to = previousValues.pop();
  return previousValues.length
    ? {from, to}
    : null;
}

function BillingTable ({data, showQuota = true}) {
  let currentInfo, previousInfo;
  const {quota, previousQuota, values} = data || {};
  const renderQuotaColumn = showQuota && (quota || previousQuota);
  const firstValue = (values || []).filter(v => v.value).shift();
  const lastValue = (values || []).filter(v => v.value).pop();
  const previousValues = getPreviousValues(values) || false;
  currentInfo = {
    quota,
    value: lastValue ? lastValue.value : false,
    dates: {
      from: firstValue ? firstValue.date : false,
      to: lastValue ? lastValue.date : false
    }
  };
  previousInfo = {
    quota: previousQuota,
    value: lastValue ? lastValue.previous : false,
    dates: {
      from: previousValues?.from?.prevDate || false,
      to: previousValues?.to?.prevDate || false
    }
  };
  const extra = lastValue && lastValue.value > lastValue.previous;
  const renderValue = (value) => {
    if (!isNaN(value) && value) {
      return `$${value}`;
    }
    return '';
  };
  const renderDates = (date = {}) => {
    const {from, to} = date;
    if (from && to) {
      return `${from} - ${to}`;
    }
    return '-';
  };
  const renderInfo = (title, info, isCurrent) => {
    const valueClassNames = [
      !info ? styles.pending : false,
      renderQuotaColumn && info && info.value > info.quota ? styles.bold : false,
      styles.value
    ].filter(Boolean);
    const quotaClassNames = [
      !info ? styles.pending : false,
      styles.value
    ].filter(Boolean);
    return (
      <tr>
        <td>
          {title}
        </td>
        <td className={valueClassNames.join(' ')}>
          {renderDates(info ? info.dates : undefined)}
        </td>
        <td className={valueClassNames.join(' ')}>
          {renderValue(info ? info.value : undefined)}
        </td>
        {
          renderQuotaColumn &&
          (
            <td className={quotaClassNames.join(' ')}>
              {renderValue(info ? info.quota : undefined)}
            </td>
          )
        }
        <td className={[styles.quota, styles.borderless].join(' ')}>
          {
            isCurrent && extra
              ? <Icon type="caret-up" />
              : '\u00A0'
          }
        </td>
      </tr>
    );
  };
  return (
    <div className={styles.container}>
      <table className={styles.table}>
        <tbody>
          <tr>
            <td className={styles.borderless} colSpan={2}>{'\u00A0'}</td>
            {
              renderQuotaColumn && (<td>Quota</td>)
            }
            <td className={[styles.quota, styles.borderless].join(' ')}>{'\u00A0'}</td>
          </tr>
          {renderInfo('Current', currentInfo, true)}
          {renderInfo('Previous', previousInfo)}
        </tbody>
      </table>
    </div>
  );
}

export default observer(BillingTable);
