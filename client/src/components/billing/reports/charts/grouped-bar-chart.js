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
import {observer} from 'mobx-react';
import BarChart from './bar-chart';
import {colors} from './colors';
import styles from './charts.css';

function GroupedBarChart ({data, onSelect, title, getBarAndNavigate}) {
  const groups = Object.keys(data || {});
  const itemsCount = groups.map(group => Object.keys(data[group] || {}).length);
  const total = itemsCount.reduce((r, c) => r + c, 0);
  const charts = groups.map((group, index) => (
    <BarChart
      key={group}
      data={data[group]}
      title={group}
      getBarAndNavigate={getBarAndNavigate}
      subChart
      style={{
        width: total > 0 ? `${100.0 * itemsCount[index] / total}%` : `${100 / itemsCount.length}%`,
        display: 'inline-block'
      }}
      onSelect={onSelect ? key => onSelect({group, key}) : undefined}
      axisPosition={index === 0 ? 'left' : 'right'}
      colors={{
        current: {background: colors.orange, color: colors.orange}
      }}
    />
  ));
  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column'
      }}
    >
      {title && <div className={styles.title}>{title}</div>}
      <div style={{
        display: 'flex',
        width: '100%',
        height: '100%',
        position: 'relative'
      }}>
        <div style={{
          width: '100%',
          height: '100%',
          display: 'block',
          position: 'relative'
        }}>
          {charts}
        </div>
      </div>
    </div>
  );
}

export default observer(GroupedBarChart);
