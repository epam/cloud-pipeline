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
import {observer} from 'mobx-react';
import {costTickFormatter} from '../utilities';
import styles from './billing-table.css';

function BillingTable ({data, showQuota = true}) {
  let currentInfo, previousInfo;
  const {quota, previousQuota, values} = data || {};
  const renderQuotaColumn = showQuota && (quota || previousQuota);
  const [firstValue] = (values || []).filter(v => v.value);
  const lastValue = (values || []).filter(v => v.value).pop();
  const lastValueIndex = (values || []).indexOf(lastValue);
  const [firstPreviousValue] = (values || []).filter(v => v.previous);
  const lastPreviousValue = (values || [])
    .slice(0, lastValueIndex + 1)
    .filter(v => v.previous)
    .pop();
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
    value: lastPreviousValue ? lastPreviousValue.previous : false,
    dates: {
      from: firstPreviousValue ? firstPreviousValue.prevDate : false,
      to: lastPreviousValue ? lastPreviousValue.prevDate : false
    }
  };
  const extra = lastValue && lastValue.value > lastValue.previous;
  const renderValue = (value) => {
    if (!isNaN(value) && value) {
      return costTickFormatter(value);
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
    const dateClassNames = [
      !info ? styles.pending : false,
      styles.value
    ].filter(Boolean);
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
        <td className={dateClassNames.join(' ')}>
          {renderDates(info ? info.dates : undefined)}
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
