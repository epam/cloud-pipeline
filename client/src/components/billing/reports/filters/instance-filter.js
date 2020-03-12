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
import {Radio} from 'antd';

const filters = {
  value: {
    dataSample: 'value',
    previousDataSample: 'previous'
  },
  usage: {
    dataSample: 'usage',
    previousDataSample: 'previousUsage',
    title: 'Usage (hours)'
  },
  runsCount: {
    dataSample: 'runsCount',
    previousDataSample: 'previousRunsCount',
    title: 'Runs count'
  }
};

function handle (value, handler) {
  const {dataSample, previousDataSample} = filters[value];
  handler(dataSample, previousDataSample);
}

export default function ({onChange, value}) {
  return (
    <Radio.Group value={value} onChange={e => handle(e.target.value, onChange)}>
      <Radio.Button key="value" value="value">Cost</Radio.Button>
      <Radio.Button key="usage" value="usage">Usage (hours)</Radio.Button>
      <Radio.Button key="runsCount" value="runsCount">Runs</Radio.Button>
    </Radio.Group>
  );
}

export {filters as InstanceFilters};
