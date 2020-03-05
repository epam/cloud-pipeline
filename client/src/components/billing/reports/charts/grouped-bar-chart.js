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
    height: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
  };

  charts = {};

  static defaultProps = {
    height: '500px'
  };

  componentDidMount () {
    const {imageGenerator} = this.props;
    if (imageGenerator) {
      imageGenerator.registerGenerator(this.generateImage);
    }
    this.charts = {};
  }

  generateImage = () => {
    const {title} = this.props;
    const {groups} = this.billingData;
    const totalWidth = (groups || [])
      .map(group => this.charts[group] || {})
      .map(({width}) => (width) || 0)
      .reduce((r, c) => r + c, 0);
    const reports = (groups || [])
      .map(group => this.charts[group] || {});
    const titleHeight = 30;
    const totalHeight = titleHeight + Math.max(0, ...(groups || [])
      .map(group => this.charts[group] || {})
      .map(({height}) => (height) || 0));
    return new Promise((resolve) => {
      const canvasElement = document.createElement('canvas');
      canvasElement.width = totalWidth;
      canvasElement.height = totalHeight;
      document.body.style.overflowY = 'hidden';
      document.body.appendChild(canvasElement);
      const ctx = canvasElement.getContext('2d');
      ctx.fillStyle = 'white';
      ctx.fillRect(0, 0, totalWidth, totalHeight);
      ctx.fillStyle = 'rgb(89, 89, 89)';
      ctx.font = 'bold 9pt sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
      ctx.fillText(
        title,
        totalWidth / 2,
        titleHeight
      );
      let x = 0;
      reports.forEach((canvasData) => {
        ctx.putImageData(canvasData, x, titleHeight);
        x += canvasData.width;
      });
      document.body.removeChild(canvasElement);
      document.body.style.overflowY = 'unset';
      resolve(ctx.getImageData(0, 0, totalWidth, totalHeight));
    });
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
    };
  };

  onImageDataReceived = (group) => (data) => {
    this.charts[group] = data;
  };

  render () {
    const {title, height, request, onSelect} = this.props;
    const {data, itemsCount, groups, total} = this.billingData;
    return (
      <div style={{position: 'relative'}}>
        {title && <div className={styles.title}>{title}</div>}
        <div style={{position: 'relative', display: 'block', height}}>
          {groups.length > 0 ? groups.map((group, index) => (
            <BarChart
              key={group}
              request={request}
              data={data[group]}
              title={group}
              subChart
              style={Object.assign({
                width: total > 0
                  ? `${100.0 * itemsCount[index] / total}%`
                  : `${100 / itemsCount.length}%`,
                display: 'inline-block',
                height
              })}
              onSelect={onSelect ? ({key} = {}) => onSelect({group, key}) : undefined}
              onScaleSelect={onSelect ? () => onSelect({group}) : undefined}
              axisPosition={index === 0 ? 'left' : 'right'}
              useImageConsumer={false}
              onImageDataReceived={this.onImageDataReceived(group)}
            />
          )) : '\u00A0'
          }
        </div>
      </div>
    );
  };
}

const GroupedBarChartWithImageGenerator = Export.ImageConsumer.Generator(
  observer(GroupedBarChart)
);

const GroupedBarChartWithImageConsumer = ({...props}) => (
  <Export.ImageConsumer order={2}>
    <GroupedBarChartWithImageGenerator {...props} />
  </Export.ImageConsumer>
);

export default observer(GroupedBarChartWithImageConsumer);
