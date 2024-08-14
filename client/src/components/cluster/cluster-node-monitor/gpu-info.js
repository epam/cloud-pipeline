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
  message,
  DatePicker,
  Button
} from 'antd';
import classNames from 'classnames';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import ClusterNodeGPUUsage from '../../../models/cluster/ClusterNodeGPUUsage';
import TimelineChart from '../../special/timeline-chart';
import HeatMapChart from '../../special/heat-map-chart';
import {extractHeatmapData, extractTimelineData} from './utils';
import {renderHeatmapTooltip, renderTimelineTooltip} from './tooltips';
import moment from 'moment-timezone';
import styles from './gpu-info.css';

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

const RANGES = {
  week: 'week',
  day: 'day',
  hour: 'hour'
};

const DATASET_COLORS = {
  [DATASET_TYPES.gpuActive]: '#108ee9',
  [DATASET_TYPES.gpuUtilization]: '#09ab5a',
  [DATASET_TYPES.memory]: '#f04134'
};

const FETCH_DELAY = 1000;

function Divider () {
  return (
    <div className={classNames('cp-divider', 'vertical')}>
      {'\u00A0'}
    </div>
  );
}

@inject('themes')
@observer
class GPUInfoTab extends React.Component {
  state = {
    measure: MEASURES.average,
    selectedGPU: 'All',
    hideDatasets: [],
    pending: false,
    from: undefined,
    to: undefined,
    chartsFrom: undefined,
    chartsTo: undefined,
    metrics: undefined,
    rangeKey: undefined
  };

  rangeChangeTimer;
  fetchTimer;

  componentDidMount () {
    this.initRanges();
  }

  componentDidUpdate (prevProps, prevState) {
    const {nodeName} = this.props;
    if (prevProps.nodeName !== nodeName) {
      this.initRanges();
    }
  }

  initRanges = () => {
    const from = moment.utc('2024-08-12 23:59:59').local()
      .subtract(1, 'days').format('YYYY-MM-DD HH:mm:ss');
    const to = '2024-08-12 23:59:59';
    this.setState({
      from,
      to,
      rangeKey: RANGES.day
    }, () => this.fetchMetrics(true));
  };

  @computed
  get themeConfiguration () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration;
    }
    return {};
  }

  @computed
  get nodeCreationDate () {
    const {node} = this.props;
    if (node && node.loaded) {
      const mock = moment.utc('2024-08-12 23:59:59').local()
        .subtract(7, 'days').format('YYYY-MM-DD HH:mm:ss');
      return mock;
      return node.value?.creationTimestamp;
    }
  }

  get GPUDeviceName () {
    const {metrics} = this.state;
    return metrics?.global?.gpuDeviceName;
  }

  fetchMetricsDelayed = (updateChartsRange) => {
    if (this.fetchTimer) {
      clearTimeout(this.fetchTimer);
      this.fetchTimer = null;
    }
    this.fetchTimer = setTimeout(() => {
      this.fetchMetrics(updateChartsRange);
    }, FETCH_DELAY);
  };

  fetchMetrics = (updateChartsRange = false) => {
    const {nodeName} = this.props;
    if (!nodeName) {
      return;
    }
    const nodeNameMock = 'i-016c2dfd271b1254b';
    this.setState({pending: true}, async () => {
      const {nodeName} = this.props;
      const {from, to} = this.state;
      const request = new ClusterNodeGPUUsage(
        nodeNameMock,
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
        pending: false,
        chartsFrom: updateChartsRange
          ? moment(this.state.from).unix()
          : this.state.chartsFrom,
        chartsTo: updateChartsRange
          ? moment(this.state.to).unix()
          : this.state.chartsTo
      });
    });
  };

  onRangeChanged = (range = {}) => {
    const {chartsFrom = 0, chartsTo = 0} = this.state;
    const {
      fromZoom = false,
      fromDrag = false,
      from,
      to
    } = range;
    if (this.rangeChangeTimer) {
      return;
    }
    if ((chartsFrom !== from || chartsTo !== to) && (fromZoom || fromDrag)) {
      this.rangeChangeTimer = setTimeout(() => this.setState({
        chartsFrom: from,
        chartsTo: to,
        from: moment.unix(from).utc().format('YYYY-MM-DD HH:mm:ss')
      }, () => {
        clearTimeout(this.rangeChangeTimer);
        this.rangeChangeTimer = undefined;
        if (fromZoom) {
          this.fetchMetricsDelayed();
        }
      }));
    }
  };

  renderOverallMetrics = () => {
    const renderMetricsCard = ({title, value, key}) => (
      <div key={key} className={styles.metricsCard}>
        {value}
        <span className={styles.title}>{title}</span>
      </div>
    );
    const gpuUsage = this.state.metrics?.global?.gpuUsage || {};
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
                {Math.round(gpuUsage?.gpuUtilization?.average) || 0}%
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
                {Math.round(gpuUsage?.gpuMemoryUtilization?.average) || 0}%
              </span>)
          }].map(renderMetricsCard)}
      </div>
    );
  };

  renderChartControls = () => {
    const {hideDatasets, measure, metrics} = this.state;
    const onMeasureChange = ({key}) => this.setState({measure: key});
    const onGPUChange = ({key}) => this.setState({selectedGPU: key});
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
          <Menu.Item style={{minWidth: 80}} key={value}>
            {`${value[0].toUpperCase()}${value.substring(1)}`}
          </Menu.Item>
        ))}
      </Menu>
    );
    const gpuKeys = Object.keys((metrics?.charts || [])[0]?.gpuDetails || {});
    const gpuMenu = (
      <Menu onClick={onGPUChange}>
        {['All', ...gpuKeys].map(key => (
          <Menu.Item style={{minWidth: 80}} key={key}>
            {key}
          </Menu.Item>
        ))}
      </Menu>
    );
    const renderLegendMarker = (stroke) => (
      <svg height="10" width="20">
        <line
          x1={0}
          y1={5}
          x2={20}
          y2={5}
          stroke={stroke}
          fill={'none'}
          strokeWidth={2}
        />
        <circle cx="10" cy="5" r="3"
          strokeWidth="2"
          fill={this.themeConfiguration['@card-background-color'] || 'white'}
          stroke={stroke}
        />
      </svg>
    );
    const onStartChange = (date) => {
      this.setState({
        from: date.startOf('day').format('YYYY-MM-DD HH:mm:ss')
      }, () => this.fetchMetrics(true));
    };
    const onEndChange = (date) => {
      this.setState({
        to: date.endOf('day').format('YYYY-MM-DD HH:mm:ss')
      }, () => this.fetchMetrics(true));
    };
    const setRange = ({key}) => {
      const toMock = moment('2024-08-12 23:59:59');
      let from;
      if (key === RANGES.hour) {
        from = moment.utc(toMock).local().subtract(1, 'hours').format('YYYY-MM-DD HH:mm:ss');
      }
      if (key === RANGES.day) {
        from = moment.utc(toMock).local().subtract(1, 'days').format('YYYY-MM-DD HH:mm:ss');
      }
      if (key === RANGES.week) {
        from = moment.utc(toMock).local().subtract(7, 'days').format('YYYY-MM-DD HH:mm:ss');
      }
      this.setState({
        from,
        chartsFrom: moment(from).unix(),
        chartsTo: moment(toMock).unix(),
        rangeKey: key
      }, this.fetchMetrics());
    };
    const renderRangeControls = () => (
      <div className={styles.rangeControls}>
        <Dropdown
          overlay={(
            <Menu
              onClick={setRange}
              style={{cursor: 'pointer'}}
              selectedKeys={[]}
            >
              {Object.keys(RANGES).map(rangeKey => (
                <Menu.Item
                  key={rangeKey}
                >
                  {`Last ${rangeKey}`}
                </Menu.Item>
              ))}
            </Menu>
          )}>
          <Button>
            Set range <Icon type="down" />
          </Button>
        </Dropdown>
        <Divider />
        <DatePicker
          format="YYYY-MM-DD HH:mm"
          placeholder="Start"
          onChange={onStartChange}
          value={moment.utc(this.state.from)}
        />
        <Divider />
        <DatePicker
          format="YYYY-MM-DD HH:mm"
          placeholder="End"
          onChange={onEndChange}
          value={moment.utc(this.state.to)}
        />
      </div>
    );
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div className={styles.chartControls}>
          <b>{this.GPUDeviceName}</b>
          <div className={styles.legend}>
            {Object.entries(DATASET_TYPES).map(([key, value], index) => {
              const color = hideDatasets.includes(index)
                ? this.themeConfiguration['@application-color-disabled'] || '#8c8c8c'
                : DATASET_COLORS[value];
              return (
                <div
                  key={key}
                  style={{display: 'flex', gap: '5px', alignItems: 'center', userSelect: 'none'}}
                >
                  {renderLegendMarker(color)}
                  <span
                    className={styles.legendItem}
                    style={{color}}
                    onClick={() => toggleDataset(index)}
                  >
                    {value}
                  </span>
                </div>
              );
            })}
          </div>
          <div style={{display: 'flex', gap: '15px', marginLeft: 'auto', alignItems: 'center'}}>
            <div style={{display: 'flex', gap: '5px'}}>
              <span>GPU:</span>
              <Dropdown overlay={gpuMenu}>
                <a>
                  {this.state.selectedGPU} <Icon type="down" />
                </a>
              </Dropdown>
            </div>
            <div style={{display: 'flex', gap: '5px'}}>
              <span>Measure:</span>
              <Dropdown overlay={menu}>
                <a>
                  {`${measure[0].toUpperCase()}${measure.substring(1)}`} <Icon type="down" />
                </a>
              </Dropdown>
            </div>
            {renderRangeControls()}
          </div>
        </div>
      </div>
    );
  };

  renderTelemetry = () => {
    const {
      measure,
      metrics,
      chartsFrom,
      chartsTo,
      hideDatasets,
      to
    } = this.state;
    const timelineDataset = extractTimelineData({
      metrics,
      measure,
      hideDatasets,
      from: this.nodeCreationDate,
      to
    });
    const heatmapDataset = extractHeatmapData({
      metrics,
      measure,
      from: this.nodeCreationDate,
      to
    });
    console.log('datasets', {
      timelineDataset,
      heatmapDataset
    });
    return (
      <div className={styles.telemetry}>
        {this.renderChartControls()}
        <TimelineChart
          options={{showHorizontalLines: true, animateZoom: false}}
          style={{flex: 1, width: '100%', height: '300px'}}
          datasets={timelineDataset}
          datasetOptions={timelineDataset.map(d => ({
            color: d.color
          }))}
          onRangeChanged={this.onRangeChanged}
          from={chartsFrom}
          to={chartsTo}
          hover={{getHoveredElementsInfo: (hoveredItems, styles) => renderTimelineTooltip({
            hoveredItems,
            styles,
            measure,
            themeConfiguration: this.themeConfiguration
          })}}
          hoverContainerClassName="cp-panel"
        />
        <div style={{display: 'flex', flexDirection: 'column', gap: '5px'}}>
          <HeatMapChart
            datasets={heatmapDataset}
            onRangeChanged={this.onRangeChanged}
            from={chartsFrom}
            to={chartsTo}
            hover={{getHoveredElementsInfo: (hoveredItem, styles) => renderHeatmapTooltip({
              hoveredItem,
              styles,
              measure,
              metrics,
              themeConfiguration: this.themeConfiguration
            })}}
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
