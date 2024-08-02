/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Icon,
  Dropdown,
  Menu,
  message
} from 'antd';
import classNames from 'classnames';
import ClusterNodeGPUUsage from '../../../models/cluster/ClusterNodeGPUUsage';
import TimelineChart from '../../special/timeline-chart';
import HeatMapChart from '../../special/heat-map-chart';
import styles from './gpu-info.css';
import {extractHeatmapData, extractTimelineData} from './utils';

const METRICS_TYPES = {
  memoryUtilization: 'memoryUtilization',
  gpuUtilization: 'gpuUtilization'
};

const MEASURES = {
  min: 'min',
  max: 'max',
  average: 'average'
};

const DATASET_TYPES = {
  gpuActive: 'Time GPU Active',
  gpuUtilization: 'GPU Utilization',
  memory: 'GPU Memory'
};

// TODO: use ThemeManager for colors
const DATASET_COLORS = {
  [DATASET_TYPES.gpuActive]: '#108ee9',
  [DATASET_TYPES.gpuUtilization]: '#09ab5a',
  [DATASET_TYPES.memory]: '#f04134'
};

class GPUInfoTab extends React.Component {
  state = {
    measure: MEASURES.average,
    hideDatasets: [],
    pending: false,
    from: '2024-07-30 11:54:59',
    to: '2024-07-30 17:35:59',
    chartsFrom: undefined,
    chartsTo: undefined,
    metrics: undefined
  };
  // TODO: add controls for range (from, to) + fetching

  debounceTimer;

  componentDidMount () {
    this.fetchMetrics();
  }

  componentDidUpdate (prevProps) {
    const {nodeName} = this.props;
    if (prevProps.nodeName !== nodeName) {
      this.fetchMetrics();
    }
  }

  fetchMetrics = () => {
    const {nodeName} = this.props;
    if (!nodeName) {
      return;
    }
    this.setState({pending: true}, async () => {
      const {nodeName} = this.props;
      const {from, to} = this.state;
      const request = new ClusterNodeGPUUsage(
        nodeName,
        from,
        to
      );
      await request.fetch();
      if (request.error) {
        message.error(request.error, 5);
        this.setState({pending: false});
        return;
      }
      return this.setState({
        metrics: request.value || {},
        pending: false
      });
    });
  }

  renderOverallMetrics = () => {
    const renderMetricsCard = ({title, value, key}) => (
      <div key={key} className={styles.metricsCard}>
        {value}
        <span className={styles.title}>{title}</span>
      </div>
    );
    return (
      <div className={styles.overallMetrics}>
        {[
          {
            key: METRICS_TYPES.gpuUtilization,
            title: 'GPU Utilization',
            value: (
              <span
                style={{color: '#09ab5a'}}
                className={classNames(styles.value, 'cp-primary')}
              >
                {Math.round(this.state.metrics?.global.gpuUsage.gpuUtilization.average) || 0}%
              </span>)
          },
          {
            key: METRICS_TYPES.memoryUtilization,
            title: 'GPU Memory Utilization',
            value: (
              <span
                style={{color: '#f04134'}}
                className={classNames(styles.value, 'cp-warning')}
              >
                {Math.round(this.state.metrics?.global.gpuUsage.gpuMemoryUtilization.average) || 0}%
              </span>)
          }].map(renderMetricsCard)}
      </div>
    );
  };

  renderChartControls = () => {
    const {hideDatasets, measure} = this.state;
    const onMeasureChange = ({key}) => this.setState({measure: key});
    const toggleDataset = (index) => {
      const {hideDatasets} = this.state;
      this.setState({hideDatasets: hideDatasets.includes(index)
        ? hideDatasets.filter(i => i !== index)
        : [...hideDatasets, index]
      });
    };
    const menu = (
      <Menu onClick={onMeasureChange}>
        {Object.values(MEASURES).map(value => (
          <Menu.Item key={value}>
            {`${value[0].toUpperCase()}${value.substring(1)}`}
          </Menu.Item>
        ))}
      </Menu>
    );
    return (
      <div className={styles.chartControls}>
        <div className={styles.legend}>
          {Object.entries(DATASET_TYPES).map(([key, value], index) => (
            <span
              className={styles.legendItem}
              key={key}
              style={{color: hideDatasets.includes(index)
                ? '#8c8c8c'
                : DATASET_COLORS[value]}}
              onClick={() => toggleDataset(index)}
            >
              {value}
            </span>
          ))}
        </div>
        <div>
          <Dropdown overlay={menu}>
            <a>
              {`${measure[0].toUpperCase()}${measure.substring(1)}`} <Icon type="down" />
            </a>
          </Dropdown>
        </div>
      </div>
    );
  };

  onRangeChanged = (range = {}) => {
    const {chartsFrom = 0, chartsTo = 0} = this.state;
    const {from, to} = range;
    if (!from || !to) {
      return;
    }
    if (this.debounceTimer) {
      return;
    }
    if ((chartsFrom !== from || chartsTo !== to)) {
      this.debounceTimer = setTimeout(() => this.setState({
        chartsFrom: from,
        chartsTo: to
      }, () => {
        clearTimeout(this.debounceTimer);
        this.debounceTimer = undefined;
      }));
    }
  };

  renderTelemetry = () => {
    const {
      measure,
      metrics,
      chartsFrom,
      chartsTo,
      hideDatasets
    } = this.state;
    const timelineDataset = extractTimelineData(metrics, measure, hideDatasets);
    const heatmapDataset = extractHeatmapData(metrics, measure);
    return (
      <div className={styles.telemetry}>
        {this.renderChartControls()}
        <TimelineChart
          options={{showHorizontalLines: true}}
          style={{flex: 1, width: '100%', height: '300px'}}
          datasets={timelineDataset}
          datasetOptions={timelineDataset.map(d => ({
            color: d.color
          }))}
          onRangeChanged={this.onRangeChanged}
          from={chartsFrom}
          to={chartsTo}
        />
        <div style={{display: 'flex', flexDirection: 'column', gap: '5px'}}>
          <HeatMapChart
            datasets={heatmapDataset}
            onRangeChanged={this.onRangeChanged}
            from={chartsFrom}
            to={chartsTo}
          />
        </div>
      </div>
    );
  };

  render () {
    return (
      <div className={styles.container}>
        {this.renderOverallMetrics()}
        {this.renderTelemetry()}
      </div>
    );
  }
}

export default GPUInfoTab;
