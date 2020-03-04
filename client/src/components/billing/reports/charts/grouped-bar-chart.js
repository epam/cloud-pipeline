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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import BarChart from './bar-chart';
import styles from './charts.css';
import Export from '../export';

class GroupedBarChart extends React.Component {
  static propTypes = {
    request: PropTypes.object,
    onSelect: PropTypes.func,
    title: PropTypes.string,
    height: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  };

  static defaultProps = {
    height: '500px'
  };

  get billingData () {
    const {request = {}} = this.props;
    const data = request.loaded ? (request.value || {}) : {};
    const groups = Object.keys(data || {});
    const itemsCount = groups.map(group => Object.keys(data[group] || {}).length);
    const total = itemsCount.reduce((r, c) => r + c, 0);
    return {
      data,
      groups,
      itemsCount,
      total
    }
  };

  render () {
    const {title, height, request, onSelect} = this.props;
    const {data, itemsCount, groups, total} = this.billingData;
    return (
      <Export.ImageConsumer
        style={{position: 'relative'}}
        order={2}
      >
        {title && <div className={styles.title}>{title}</div>}
        <div style={{position: 'relative', display: 'block', height}}>
          {
            groups.length > 0 ? groups.map((group, index) => (
              <BarChart
                key={group}
                request={request}
                data={data[group]}
                title={group}
                subChart
                style={Object.assign({
                  width: total > 0 ? `${100.0 * itemsCount[index] / total}%` : `${100 / itemsCount.length}%`,
                  display: 'inline-block',
                  height
                })}
                onSelect={onSelect ? ({key} = {}) => onSelect({group, key}) : undefined}
                onScaleSelect={onSelect ? () => onSelect({group}) : undefined}
                axisPosition={index === 0 ? 'left' : 'right'}
                useImageConsumer={false}
              />
          )) : '\u00A0'
          }
        </div>
      </Export.ImageConsumer>
    );
  };
};

export default observer(GroupedBarChart);
