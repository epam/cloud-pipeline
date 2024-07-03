/*
 *
 *  * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

import React from 'react';
import {Radio} from 'antd';
import {observer} from 'mobx-react';
import {StorageMetrics, parseStorageMetrics} from '../../navigation/metrics';
import BillingNavigation from '../../navigation';

const filters = {
  value: {
    dataSample: 'value',
    previousDataSample: 'previous',
    key: 'value'
  },
  usage: {
    dataSample: 'usage',
    previousDataSample: 'previousUsage',
    key: 'usage'
  }
};

function StorageFilter ({filters}) {
  const {
    metrics,
    metricsNavigation
  } = filters;
  return (
    <Radio.Group
      value={parseStorageMetrics(metrics)}
      onChange={e => metricsNavigation(e.target.value)}
      size="small"
    >
      <Radio.Button key={StorageMetrics.costs} value={StorageMetrics.costs}>Costs</Radio.Button>
      <Radio.Button key={StorageMetrics.volume} value={StorageMetrics.volume}>Volume</Radio.Button>
    </Radio.Group>
  );
}

export default BillingNavigation.attach(
  observer(StorageFilter)
);

export {filters as StorageFilters};
