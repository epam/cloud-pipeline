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
import PropTypes from 'prop-types';
import {Row} from 'antd';
import styles from './Charts.css';
import Chart from 'chart.js';
import moment from 'moment';

@observer
export default class NodeChart extends React.Component {

  static propTypes = {
    className: PropTypes.string,
    usage: PropTypes.object
  };

  static title = null;
  static noDataAvailableMessage = 'No data available';

  chart;
  static chartType = 'line';
  static chartOptions = {};
  static plugins = [];

  getDate = ({startTime, endTime}) => {
    const startLocalTime = moment.utc(startTime);
    const endLocalTime = moment.utc(endTime);
    const diff = moment.duration(endLocalTime.diff(startLocalTime));
    startLocalTime.add(diff / 2);
    return moment(startLocalTime.toDate()).format('HH:mm:ss');
  };

  updateChartData = ({labels, datasets}) => {
    if (!this.chart.data.labels) {
      this.chart.data.labels = labels;
    }
    if (!this.chart.data.datasets || this.chart.data.datasets.length !== datasets.length) {
      this.chart.data.datasets = datasets;
    }
    const existedItems = this.chart.data.labels.length;
    const replaceItems = Math.min(existedItems, labels.length);
    for (let i = 0; i < replaceItems; i++) {
      this.chart.data.labels[i] = labels[i];
      for (let j = 0; j < datasets.length; j++) {
        this.chart.data.datasets[j].data[i] = datasets[j].data[i];
        this.chart.data.datasets[j].drawDataLabel = datasets[j].drawDataLabel;
        if (this.chart.data.datasets[j].dataLabel && datasets[j].dataLabel) {
          this.chart.data.datasets[j].dataLabel[i] = datasets[j].dataLabel[i];
        }
      }
    }
    this.chart.data.labels.splice(replaceItems, existedItems - replaceItems);
    for (let j = 0; j < datasets.length; j++) {
      this.chart.data.datasets[j].data.splice(replaceItems, existedItems - replaceItems);
      this.chart.data.datasets[j].drawDataLabel = datasets[j].drawDataLabel;
      if (this.chart.data.datasets[j].dataLabel) {
        this.chart.data.datasets[j].dataLabel.splice(replaceItems, existedItems - replaceItems);
      }
    }
    for (let i = replaceItems; i < labels.length; i++) {
      this.chart.data.labels.push(labels[i]);
      for (let j = 0; j < datasets.length; j++) {
        this.chart.data.datasets[j].drawDataLabel = datasets[j].drawDataLabel;
        this.chart.data.datasets[j].data[i] = datasets[j].data[i];
        if (this.chart.data.datasets[j].dataLabel && datasets[j].dataLabel) {
          this.chart.data.datasets[j].dataLabel[i] = datasets[j].dataLabel[i];
        }
      }
    }
    this.chart.update();
  };

  renderChart (entries) {};

  initializeChart = (canvas) => {
    if (canvas) {
      const ctx = canvas.getContext('2d');
      this.chart = new Chart(ctx, {
        type: this.constructor.chartType,
        data: {},
        options: {
          chartArea: {
            backgroundColor: '#fcfcfc'
          },
          ...this.constructor.chartOptions
        },
        plugins: this.constructor.plugins
      });
    }
  };

  renderContent () {
    const style = {
      width: '100%',
      height: '100%'
    };
    if (this.props.usage && this.props.usage.pending) {
      style.opacity = 0.75;
    }
    return (
      <canvas
        className={styles.chartCanvas}
        ref={this.initializeChart}
        style={style} />
    );
  };

  render () {
    const containerProps = {
      className: this.props.className || styles.container
    };
    let content;
    if (!this.props.usage || (!this.props.usage.pending && this.props.usage.error)) {
      content = (
        <Row
          className={styles.alertContainer}
          type="flex"
          justify="center"
          align="middle"
          style={{height: '100%', width: '100%'}}>
          <span className={styles.alert}>
            {
              this.props.usage && !this.props.usage.pending && this.props.usage.error
              ? this.props.usage.error
              : this.constructor.noDataAvailableMessage
            }
          </span>
        </Row>
      );
    } else {
      content = (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          width: '100%',
          height: '100%'
        }}>
          {
            this.constructor.title
              ? [
                <Row
                  key={`title_${this.constructor.title}`}
                  style={{height: 30}}
                  align="middle" justify="center">
                  <b>{this.constructor.title}</b>
                </Row>,
                <Row
                  key={`content_${this.constructor.title}`}
                  type="flex"
                  style={{
                    flex: 1,
                    position: 'relative',
                    height: '100%'
                  }}>
                  {this.renderContent()}
                </Row>
              ]
              : (
                <Row
                  type="flex"
                  style={{
                    position: 'relative',
                    height: '100%'
                  }}>
                  {this.renderContent()}
                </Row>
              )
          }
        </div>
      );
    }
    return (
      <div {...containerProps}>
        {content}
      </div>
    );
  }

  componentDidUpdate () {
    if (!this.props.usage.pending) {
      this.renderChart((this.props.usage.value || []).map(e => e));
    }
  }

  componentDidMount () {
    Chart.pluginService.register({
      beforeDraw: function (chart) {
        if (chart.config.options.chartArea && chart.config.options.chartArea.backgroundColor) {
          const ctx = chart.chart.ctx;
          const chartArea = chart.chartArea;
          ctx.save();
          ctx.fillStyle = chart.config.options.chartArea.backgroundColor;
          ctx.fillRect(
            chartArea.left,
            chartArea.top,
            chartArea.right - chartArea.left,
            chartArea.bottom - chartArea.top
          );
          ctx.restore();
        }
      }
    });
  }
}
