/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import Export from '../export';
import {costTickFormatter, dateRangeRenderer} from '../utilities';
import {discounts} from '../discounts';
import styles from './billing-table.css';

function LegendItem ({color}) {
  return (
    <div
      className={styles.legend}
      style={{backgroundColor: color}}
    >
      {'\u00A0'}
    </div>
  );
}

function BillingTable (
  {
    compute,
    storages,
    computeDiscounts,
    storagesDiscounts,
    showQuota = true,
    reportThemes,
    currentValueFn = (item) => item.value,
    previousValueFn = (item) => item.previous
  }
) {
  const summary = discounts.joinSummaryDiscounts(
    [compute, storages],
    [computeDiscounts, storagesDiscounts]
  );
  const data = summary || {};
  const [filters = {}] = [compute, storages].filter(Boolean).map(a => a.filters);
  const {
    start,
    endStrict: end,
    previousStart,
    previousEndStrict: previousEnd
  } = filters;
  let currentInfo, previousInfo;
  const {quota, previousQuota, values} = data || {};
  const renderQuotaColumn = showQuota && (quota || previousQuota);
  const lastValue = (values || [])
    .filter(v => v.value && v.initialDate <= end)
    .pop();
  const lastPreviousValue = (values || [])
    .filter(v => v.previous && v.initialDate <= previousEnd)
    .pop();
  currentInfo = {
    quota,
    value: lastValue ? currentValueFn(lastValue) : false,
    dates: {
      from: start,
      to: end
    }
  };
  previousInfo = {
    quota: previousQuota,
    value: lastPreviousValue ? previousValueFn(lastPreviousValue) : false,
    dates: {
      from: previousStart,
      to: previousEnd
    }
  };
  const quotaOverrun = quota && currentInfo?.value > quota;

  const renderValue = (value) => {
    if (!isNaN(value) && value) {
      return costTickFormatter(value);
    }
    return '-';
  };
  const renderDates = ({from, to} = {}) => dateRangeRenderer(from, to) || '-';
  const renderWarning = (currentInfo = {}, previousInfo = {}) => {
    const {value: current} = currentInfo;
    const {value: previous} = previousInfo;
    let percent = 0;
    if (current && previous && !isNaN(current) && !isNaN(previous)) {
      percent = ((current - previous) / previous * 100).toFixed(2);
    }
    const containerClassNames = classNames(
      styles.warningContainer,
      {
        [styles.negative]: percent > 0,
        'cp-danger': percent > 0,
        [styles.positive]: percent < 0,
        'cp-success': percent < 0
      }
    );
    return (
      <div className={containerClassNames}>
        {quotaOverrun && (<Icon type="bars" className={styles.quotaOverrunIcon} />)}
        {percent !== 0 && (
          <Icon
            type={percent > 0 ? 'caret-up' : 'caret-down'}
            className={styles.warningIcon}
          />
        )}
        {percent !== 0 && <span>{percent > 0 ? '+' : ''}{percent}%</span>}
      </div>
    );
  };
  const renderInfo = (title, color, info, isCurrent) => {
    const dateClassNames = classNames(
      styles.date,
      {
        [styles.pending]: !info,
        'cp-billing-table-pending': !info
      }
    );
    const valueClassNames = classNames(
      styles.value,
      {
        [styles.pending]: !info,
        'cp-billing-table-pending': !info,
        [styles.bold]: (renderQuotaColumn && info && info.value > info.quota) || isCurrent
      }
    );
    const quotaClassNames = classNames(
      styles.value,
      {
        [styles.pending]: !info,
        'cp-billing-table-pending': !info
      }
    );
    return (
      <tr>
        <td className={styles.legendRow}>
          <LegendItem color={color} />
          <span>{title}</span>
        </td>
        <td className={dateClassNames}>
          <span>{renderDates(info ? info.dates : undefined)}</span>
        </td>
        <td className={valueClassNames}>
          <span>{renderValue(info ? info.value : undefined)}</span>
        </td>
        {
          renderQuotaColumn &&
          (
            <td className={quotaClassNames}>
              <span>{renderValue(info ? info.quota : undefined)}</span>
            </td>
          )
        }
        <td className={classNames(styles.quota, styles.borderless)}>
          <span>{isCurrent && renderWarning(currentInfo, previousInfo)}</span>
        </td>
      </tr>
    );
  };
  return (
    <Export.ImageConsumer
      className={styles.container}
      order={0}
    >
      <table className={classNames(styles.table, 'cp-billing-table')}>
        <tbody>
          {
            renderQuotaColumn && (
              <tr>
                <td className={styles.borderless} colSpan={2}>{'\u00A0'}</td>
                <td>Quota</td>
                <td className={classNames(styles.quota, styles.borderless)}>{'\u00A0'}</td>
              </tr>
            )
          }
          {renderInfo('Current', reportThemes.current, currentInfo, true)}
          {renderInfo('Previous', reportThemes.previous, previousInfo)}
        </tbody>
      </table>
    </Export.ImageConsumer>
  );
}

export default inject('reportThemes')(observer(BillingTable));
