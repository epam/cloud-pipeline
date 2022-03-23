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
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {Tooltip} from 'antd';
import {getQuotaSummary, getQuotaSummariesExceeded} from './quotas';
import {periodNamesAdjective} from '../../quotas/utilities/quota-periods';
import styles from './charts.css';

function QuotaSummaryChart (
  {
    request,
    discountFn,
    data,
    quotaGroup,
    quotas
  }
) {
  const {
    usage,
    quota,
    exceeds,
    quotaSummary
  } = getQuotaSummary({
    request,
    discount: discountFn,
    data,
    quotas,
    quotaGroup
  });
  if (!quota || !usage) {
    return null;
  }
  const percent = usage / quota;
  return (
    <Tooltip
      overlay={(
        <div>
          <div><b>Usage: {Math.round(usage * 100) / 100}$</b></div>
          {
            Object.entries(quotaSummary || {}).map(([period, quota]) => (
              <div key={period}>
                {periodNamesAdjective[period] || period} quota: {Math.round(quota * 100) / 100}$
              </div>
            ))
          }
        </div>
      )}
    >
      <span
        className={
          classNames(
            styles.quotaSummaryInfo,
            {'cp-danger': exceeds}
          )
        }
        style={{cursor: 'pointer'}}
      >
        {Math.round(percent * 100)}%
      </span>
    </Tooltip>
  );
}

QuotaSummaryChart.propTypes = {
  request: PropTypes.object,
  discountFn: PropTypes.func,
  data: PropTypes.object,
  quotaGroup: PropTypes.string,
  quotas: PropTypes.object
};

function QuotaSummaryChartsTitle (
  {
    requests = [],
    discounts = [],
    data,
    quotaGroup,
    children,
    displayQuotasSummary,
    style,
    onClick,
    quotas
  }
) {
  const requestsWithDiscounts = requests.map((request, index) => ({
    request,
    discount: discounts[index],
    quotaGroup,
    data,
    quotas
  }));
  const exceeded = getQuotaSummariesExceeded(requestsWithDiscounts);
  return (
    <div
      className={
        classNames(
          styles.quotaSummary,
          {
            'cp-danger': exceeded,
            'cp-text-not-important': !exceeded
          }
        )
      }
      style={style}
    >
      <span
        style={{cursor: 'pointer'}}
        onClick={onClick}
        className={styles.quotaSummaryTitle}
      >
        {children}
      </span>
      {
        displayQuotasSummary && requestsWithDiscounts.map(({request, discount}, index) => (
          <QuotaSummaryChart
            key={`summary-${index}`}
            request={request}
            discountFn={discount}
            quotaGroup={quotaGroup}
            data={data}
            quotas={quotas}
          />
        ))
      }
    </div>
  );
}

QuotaSummaryChartsTitle.propTypes = {
  children: PropTypes.node,
  requests: PropTypes.array,
  discounts: PropTypes.array,
  data: PropTypes.object,
  quotaGroup: PropTypes.string,
  displayQuotasSummary: PropTypes.bool,
  style: PropTypes.object,
  onClick: PropTypes.func
};

export default inject('quotas')(observer(QuotaSummaryChartsTitle));
