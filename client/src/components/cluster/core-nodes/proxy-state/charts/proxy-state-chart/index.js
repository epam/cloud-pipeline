/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Spin} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  BarchartDataLabelPlugin,
  ChartClickPlugin
} from '../../../../../billing/reports/charts/extensions';
import ThemedReport from '../../../../../billing/reports/themed-report';
import Chart from 'chart.js';
import 'chart.js/dist/Chart.css';
import {HISTOGRAM_TYPES} from '../../../../../../models/cluster/ClusterNetworkUsageFilter';

@inject('reportThemes', 'themes')
@observer
class ProxyStateChart extends React.Component {
  chart;
  ctx;

  componentDidMount () {
    const {themes} = this.props;
    if (themes) {
      themes.addThemeChangedListener(this.updateChart);
    }
  }

  componentWillUnmount () {
    const {themes} = this.props;
    if (themes) {
      themes.removeThemeChangedListener(this.updateChart);
    }
  }

  componentDidUpdate (prevProps) {
    if (this.ctx) {
      const {
        data: prevData,
        options: prevOptions,
        title: prevTitle,
        onEntryClick: prevOnEntryClick
      } = prevProps;
      const {
        data,
        options,
        title,
        onEntryClick
      } = this.props;
      if (
        data !== prevData ||
        options !== prevOptions ||
        title !== prevTitle ||
        onEntryClick !== prevOnEntryClick
      ) {
        this.updateChart();
      }
    }
  }

  get noDataProvided () {
    const {data} = this.props;
    return !data || !data.entries || data.entries.length === 0;
  }

  @computed
  get fontColor () {
    const {reportThemes} = this.props;
    return reportThemes ? reportThemes.subTextColor : undefined;
  }

  updateChart = () => {
    this.chartRef(this.ctx, this.props);
  }

  chartRef = (ctx, props) => {
    if (ctx) {
      this.ctx = ctx;
      const {
        data,
        options = {},
        title,
        displayEmptyTitleRow,
        onEntryClick,
        type
      } = props || this.props;
      const opts = {
        animation: {duration: 0},
        legend: {
          display: false
        },
        title: {
          display: !!title || displayEmptyTitleRow,
          text: displayEmptyTitleRow ? '' : title,
          fontColor: this.fontColor
        },
        scales: {
          xAxes: [{
            id: 'x-axis',
            gridLines: {
              display: false
            },
            ticks: {
              autoSkip: false,
              maxRotation: 90,
              minRotation: 0,
              precision: 0,
              fontColor: this.fontColor
            }
          }],
          yAxes: [
            {
              ticks: {
                beginAtZero: true,
                stepSize: 1,
                maxTicksLimit: type === HISTOGRAM_TYPES.resource ? undefined : 5,
                fontColor: this.fontColor
              }
            }
          ]
        },
        maintainAspectRatio: false,
        plugins: {
          [BarchartDataLabelPlugin.id]: {
            valueFormatter: (value) => value
          },
          [ChartClickPlugin.id]: {
            handler: (index, scaleHovered) => {
              const {entries = []} = data || {};
              if (typeof onEntryClick === 'function') {
                onEntryClick(entries[index], index, scaleHovered);
              }
            },
            axis: type === HISTOGRAM_TYPES.resource ? 'y-axis-0' : 'x-axis'
          }
        },
        hover: {
          onHover: function (e) {
            const point = this.getElementsAtXAxis(e);
            e.target.style.cursor = point.length > 0
              ? 'pointer'
              : 'default';
          }
        },
        tooltips: {
          intersect: false,
          mode: 'index'
        },
        ...options
      };
      if (this.chart) {
        this.chart.data = data;
        this.chart.options = opts;
        this.chart.update();
      } else {
        this.chart = new Chart(ctx, {
          type: 'bar',
          data,
          options: opts,
          ...(options ?? {}),
          plugins: [
            type === HISTOGRAM_TYPES.run ? BarchartDataLabelPlugin.plugin : undefined,
            ChartClickPlugin.plugin
          ].filter(Boolean)
        });
      }
      this.chart.resize();
    }
  };
  render () {
    const {loading, style} = this.props;
    return (
      <div style={Object.assign(
        {position: 'relative'},
        style
      )}>
        <canvas
          ref={this.chartRef}
          style={{
            position: 'relative',
            width: '100%',
            height: '100%',
            maxHeight: 'calc(50vh - 70px)'
          }}
        />
        {(!loading && this.noDataProvided) ? (
          <div
            style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              transform: 'translate(-50%, -50%)'
            }}
          >
            <InboxOutlined style={{fontSize: 'large'}} />
            <span>No data</span>
          </div>
        ) : null}
        {loading ? (
          <Spin style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            transform: 'translate(-50%, -50%)'
          }} spinning={loading} />
        ) : null}
      </div>
    );
  }
}

ProxyStateChart.propTypes = {
  loading: PropTypes.bool,
  data: PropTypes.object,
  options: PropTypes.object,
  title: PropTypes.string,
  displayEmptyTitleRow: PropTypes.bool,
  onEntryClick: PropTypes.func,
  type: PropTypes.string
};

const ProxyStateChartWithThemes = (props) => {
  return (
    <ThemedReport>
      <ProxyStateChart {...props} />
    </ThemedReport>
  );
};

export default ProxyStateChartWithThemes;
