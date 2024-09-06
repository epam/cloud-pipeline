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
import PropTypes from 'prop-types';
import {
  Icon,
  Dropdown,
  Menu,
  message,
  DatePicker,
  Button,
  Spin,
  Alert
} from 'antd';
import classNames from 'classnames';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import moment from 'moment-timezone';
import ClusterNodeGPUUsage from '../../../models/cluster/ClusterNodeGPUUsage';
import TimelineChart from '../../special/timeline-chart';
import HeatMapChart from '../../special/heat-map-chart';
import {extractHeatmapData, extractTimelineData} from './utils';
import {renderHeatmapTooltip, renderTimelineTooltip} from './tooltips';
import {parseDate} from '../../special/heat-map-chart/utils';
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
  gpuMemoryUtilization: 'GPU Memory'
};

const RANGES = {
  week: 'week',
  day: 'day',
  hour: 'hour'
};

const DATASET_COLORS = {
  [DATASET_TYPES.gpuActive]: '#108ee9',
  [DATASET_TYPES.gpuUtilization]: '#09ab5a',
  [DATASET_TYPES.gpuMemoryUtilization]: '#f04134'
};

const FETCH_DELAY = 500;

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
    metrics: undefined
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
    if (prevProps.chartsData !== this.props.chartsData) {
      this.initRanges();
    }
  }

  @computed
  get themeConfiguration () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration;
    }
    return {};
  }

  @computed
  get chartsData () {
    const {chartsData} = this.props;
    return chartsData;
  }

  @computed
  get chartsBounds () {
    return {
      min: this.chartsData.instanceFrom,
      max: this.chartsData.instanceTo || parseDate(moment()).unix
    };
  }

  get GPUDeviceName () {
    const {metrics} = this.state;
    return metrics?.global?.gpuDeviceName;
  }

  initRanges = () => {
    const to = this.chartsBounds.max;
    const from = Math.max(
      moment.unix(to).local().subtract(1, 'days').unix(),
      this.chartsBounds.min
    );
    this.setState({
      from,
      to
    }, () => this.fetchMetrics(true));
  };

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
    this.setState({pending: true}, async () => {
      const {nodeName} = this.props;
      const {from, to} = this.state;
      const {min, max} = this.chartsBounds;
      const offsetRange = (to - from) / 5;
      const newFrom = Math.max(min, from - offsetRange);
      const newTo = Math.min(max, to + offsetRange);
      const fromString = moment.unix(newFrom).utc().format('YYYY-MM-DD HH:mm:ss');
      const toString = moment.unix(newTo).utc().format('YYYY-MM-DD HH:mm:ss');
      const request = new ClusterNodeGPUUsage(
        nodeName,
        fromString,
        toString
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
        ...(updateChartsRange ? {chartsFrom: this.state.from} : {}),
        ...(updateChartsRange ? {chartsTo: this.state.to} : {})
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
        from: Math.max(from, this.chartsBounds.min),
        to: Math.min(to, this.chartsBounds.max)
      }, () => {
        clearTimeout(this.rangeChangeTimer);
        this.rangeChangeTimer = undefined;
        if (fromZoom || fromDrag) {
          this.fetchMetricsDelayed();
        }
      }));
    }
  };

  renderOverallMetrics = () => {
    const {measure} = this.state;
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
                {Math.round((gpuUsage?.gpuUtilization || {})[measure]) || 0}%
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
                {Math.round((gpuUsage?.gpuMemoryUtilization || {})[measure]) || 0}%
              </span>)
          }].map(renderMetricsCard)}
      </div>
    );
  };

  renderChartControls = () => {
    const {hideDatasets, measure} = this.state;
    const onMeasureChange = ({key}) => this.setState({measure: key});
    // const onGPUChange = ({key}) => this.setState({selectedGPU: key});
    const toggleDataset = (key) => {
      const {hideDatasets} = this.state;
      this.setState({hideDatasets: hideDatasets.includes(key)
        ? hideDatasets.filter(k => k !== key)
        : [...hideDatasets, key]
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
    // const gpuKeys = Object.keys((metrics?.charts || [])[0]?.gpuDetails || {});
    // const gpuMenu = (
    //   <Menu onClick={onGPUChange}>
    //     {['All', ...gpuKeys].map(key => (
    //       <Menu.Item style={{minWidth: 80}} key={key}>
    //         {key}
    //       </Menu.Item>
    //     ))}
    //   </Menu>
    // );
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
      const from = date
        ? Math.max(
          this.chartsBounds.min,
          moment(date.startOf('day')).unix())
        : this.chartsBounds.min;
      this.setState({from}, () => this.fetchMetrics(true));
    };
    const onEndChange = (date) => {
      const to = date
        ? Math.min(
          this.chartsBounds.max,
          moment(date.endOf('day')).unix())
        : this.chartsBounds.max;
      this.setState({to}, () => this.fetchMetrics(true));
    };
    const setRange = ({key}) => {
      const to = this.chartsBounds.max;
      let from;
      if (key === RANGES.hour) {
        from = Math.max(
          moment.unix(to).local().subtract(1, 'hours').unix(),
          this.chartsBounds.min
        );
      }
      if (key === RANGES.day) {
        from = Math.max(
          moment.unix(to).local().subtract(1, 'days').unix(),
          this.chartsBounds.min
        );
      }
      if (key === RANGES.week) {
        from = Math.max(
          moment.unix(to).local().subtract(7, 'days').unix(),
          this.chartsBounds.min
        );
      }
      this.setState({
        from,
        to,
        chartsFrom: from,
        chartsTo: to
      }, this.fetchMetrics());
    };
    const disabledDate = (date) => {
      return date &&
        this.chartsData?.instanceFrom &&
        (
          date.unix() < this.chartsBounds.min ||
          date.unix() > this.chartsBounds.max
        );
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
          value={moment.unix(this.state.from)}
          disabledDate={disabledDate}
        />
        <Divider />
        <DatePicker
          format="YYYY-MM-DD HH:mm"
          placeholder="End"
          onChange={onEndChange}
          value={moment.unix(this.state.to)}
          disabledDate={disabledDate}
        />
      </div>
    );
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div className={styles.chartControls}>
          <b style={{marginRight: 10}}>{this.GPUDeviceName}</b>
          <div className={styles.legend}>
            {Object.entries(DATASET_TYPES).map(([key, value]) => {
              const color = hideDatasets.includes(key)
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
                    onClick={() => toggleDataset(key)}
                  >
                    {value}
                  </span>
                </div>
              );
            })}
          </div>
          <div style={{display: 'flex', gap: '15px', marginLeft: 'auto', alignItems: 'center'}}>
            {/* //TODO: wait for API support */}
            {/* <div style={{display: 'flex', gap: '5px'}}>
              <span>GPU:</span>
              <Dropdown overlay={gpuMenu}>
                <a>
                  {this.state.selectedGPU} <Icon type="down" />
                </a>
              </Dropdown>
            </div> */}
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
      hideDatasets
    } = this.state;
    const timelineDataset = extractTimelineData({
      metrics,
      measure,
      hideDatasets,
      from: this.chartsBounds.min,
      to: this.chartsBounds.max
    });
    const heatmapDataset = extractHeatmapData({
      metrics,
      measure,
      hideDatasets,
      from: this.chartsBounds.min,
      to: this.chartsBounds.max
    });
    return (
      <div className={styles.telemetry}>
        {this.renderChartControls()}
        <TimelineChart
          options={{showHorizontalLines: true, animateZoom: true, shiftWheel: true}}
          style={{flex: 1, width: '100%', maxHeight: '50vh', minHeight: '50vh'}}
          datasets={timelineDataset}
          datasetOptions={timelineDataset.map(d => ({
            color: d.color,
            key: d.key
          }))}
          onRangeChanged={this.onRangeChanged}
          from={chartsFrom}
          to={chartsTo}
          hover={{getHoveredElementsInfo: (hoveredItems, styles) => renderTimelineTooltip({
            hoveredItems,
            styles,
            measure,
            themeConfiguration: this.themeConfiguration,
            hideDatasets
          })}}
          hoverContainerClassName="cp-panel"
        />
        {heatmapDataset.length ? (
          <div style={{display: 'flex', flexDirection: 'column', gap: '5px'}}>
            <HeatMapChart
              options={{shiftWheel: true}}
              datasets={heatmapDataset}
              onRangeChanged={this.onRangeChanged}
              from={chartsFrom}
              to={chartsTo}
              hover={{getHoveredElementsInfo: (hoveredItem, styles) => renderHeatmapTooltip({
                hoveredItem,
                styles,
                measure,
                metrics,
                themeConfiguration: this.themeConfiguration,
                hideDatasets
              })}}
            />
          </div>
        ) : null}
      </div>
    );
  };

  render () {
    const {gpuStatisticsAvailable} = this.props;
    if (!gpuStatisticsAvailable) {
      return (
        <Alert type="warning" message="GPU statistics is not available for this node" />
      );
    }
    return (
      <div className={styles.container}>
        <Spin wrapperClassName={styles.spin} spinning={this.state.pending}>
          {this.renderOverallMetrics()}
          {this.renderTelemetry()}
        </Spin>
      </div>
    );
  }
}

GPUInfoTab.propTypes = {
  nodeName: PropTypes.string,
  chartsData: PropTypes.object,
  node: PropTypes.object,
  gpuStatisticsAvailable: PropTypes.bool
};

export default GPUInfoTab;
