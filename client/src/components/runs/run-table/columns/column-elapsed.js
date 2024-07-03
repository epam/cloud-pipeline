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
import {Row} from 'antd';
import moment from 'moment-timezone';
import evaluateRunPrice from '../../../../utils/evaluate-run-price';
import JobEstimatedPriceInfo from '../../../special/job-estimated-price-info';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

const getColumnFilter = () => {};

const renderEstimatedPrice = (item) => {
  if (!item.pricePerHour) {
    return null;
  }
  const info = evaluateRunPrice(item);
  if (item.masterRun) {
    return (
      <JobEstimatedPriceInfo>
        Cost: {info.total.toFixed(2)}$ ({info.master.toFixed(2)}$)
      </JobEstimatedPriceInfo>
    );
  }
  return (
    <JobEstimatedPriceInfo>
      Cost: {info.total.toFixed(2)}$
    </JobEstimatedPriceInfo>
  );
};

const renderRunningTime = (item) => {
  const renderTime = () => {
    if (item.endDate && item.startDate && item.status !== 'RUNNING') {
      const diff = moment.utc(item.endDate).diff(moment.utc(item.startDate), 'minutes', true);
      return (
        <span>
          {diff.toFixed(2)} min
        </span>
      );
    }
    const diff = moment.utc().diff(moment.utc(item.startDate), 'minutes', true);
    return (
      <span>
        Running for {diff.toFixed(2)} min
      </span>
    );
  };
  const estimatedPrice = renderEstimatedPrice(item);
  const time = renderTime();
  if (estimatedPrice) {
    return (
      <div>
        <Row>
          {time}
        </Row>
        <Row>
          {estimatedPrice}
        </Row>
      </div>
    );
  }
  return (
    <span>
      {time}
    </span>
  );
};

const getColumn = () => ({
  title: 'Elapsed',
  key: 'runningTime',
  className: styles.runRowElapsedTime,
  render: (run) => (
    <RunLoadingPlaceholder run={run} empty>
      {renderRunningTime(run)}
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
