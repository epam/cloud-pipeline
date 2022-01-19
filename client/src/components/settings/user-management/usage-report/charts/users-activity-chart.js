/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button, Popover
} from 'antd';
import classNames from 'classnames';
import ChartJS from 'chart.js';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import moment from 'moment-timezone';
import UsageNavigation, {runnersEqual} from '../navigation';
import {getPeriod, Period} from '../../../../special/periods';
import UserName from '../../../../special/UserName';
import styles from './users-activity-chart.css';

const DEFAULT_COLOR = '#108ee9';
const DEFAULT_BACKGROUND_COLOR = '#ffffff';
const DEFAULT_TEXT_COLOR = 'rgba(0, 0, 0, 0.65)';
const DEFAULT_ERROR_COLOR = '#ff0000';
const DEFAULT_LINE_COLOR = DEFAULT_TEXT_COLOR;

const Modes = {
  active: 'active',
  total: 'total'
};

const AllModes = Object.values(Modes);

const ModeNames = {
  [Modes.active]: 'Active',
  [Modes.total]: 'Total'
};

const Titles = {
  [Modes.active]: 'Online users count',
  [Modes.total]: 'Accumulative users count'
};

function fetchDataMock (options) {
  return new Promise((resolve) => {
    const {
      period,
      range,
      runner = []
    } = options || {};
    console.log('mocking data for', {period, range, runner});
    const {start, end} = getPeriod(period, range);
    const unit = period === Period.day ? 'hour' : 'day';
    const data = [];
    const pushTick = date => data.push({
      date: date.utc(false).format('YYYY-MM-DD HH:mm:ss.SSS'),
      count: Math.round(10 * Math.random()),
      online: Math.round(10 * Math.random())
    });
    let tick = moment(start);
    const now = moment();
    while (tick <= end && tick <= now) {
      pushTick(tick);
      tick = moment(tick).add(1, unit);
    }
    setTimeout(() => resolve(data), 200);
  });
}

function generateTicks (from, to, period) {
  const ticks = [];
  if (from < to) {
    const format = period === Period.day ? 'HH:mm' : 'D MMM YYYY';
    const unit = period === Period.day ? 'hour' : 'day';
    const pushTick = tick => ticks.push({tick: tick, label: tick.format(format)});
    let tick = moment(from);
    while (tick <= to) {
      pushTick(tick);
      tick = moment(tick).add(1, unit);
    }
  }
  return ticks;
}

class Chart extends React.PureComponent {
  componentDidMount () {
    this.initializeChart();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.initializeChart();
  }

  initializeChart = () => {
    if (!this.canvas) {
      return;
    }
    const {
      data = [],
      labels = [],
      title,
      background = DEFAULT_BACKGROUND_COLOR,
      color = DEFAULT_COLOR,
      textColor = DEFAULT_TEXT_COLOR,
      lineColor = DEFAULT_LINE_COLOR,
      pending = false,
      onClick
    } = this.props;
    const chartOptions = {
      animation: {duration: 0},
      maintainAspectRatio: false,
      title: {
        display: !!title,
        text: title,
        fontColor: textColor
      },
      scales: {
        xAxes: [{
          gridLines: {
            drawOnChartArea: false,
            color: lineColor,
            zeroLineColor: lineColor
          },
          ticks: {
            display: true,
            maxRotation: 45,
            callback: (date) => date || '',
            fontColor: textColor
          },
          offset: true
        }],
        yAxes: [{
          min: 0,
          gridLines: {
            display: !pending,
            color: lineColor,
            zeroLineColor: lineColor
          },
          ticks: {
            display: !pending,
            fontColor: textColor
          }
        }]
      },
      legend: {
        display: false
      },
      onClick: (e) => {
        const xAxis = this.chart.scales['x-axis-0'];
        const yAxis = this.chart.scales['y-axis-0'];
        if (xAxis && yAxis) {
          const canvasPosition = ChartJS.helpers.getRelativePosition(e, this.chart);
          const index = xAxis.getValueForPixel(canvasPosition.x);
          if (data[index] !== undefined) {
            const x = xAxis.getPixelForTick(index);
            const y = yAxis.getPixelForValue(data[index]);
            if (onClick) {
              onClick(index, {x, y});
            }
          }
        }
      },
      tooltips: {
        mode: 'index',
        intersect: false
      }
    };
    const dataConfiguration = {
      labels,
      datasets: [
        {
          label: 'Count',
          data,
          fill: false,
          borderColor: color,
          borderWidth: 2,
          backgroundColor: background,
          pointRadius: 2,
          pointBackgroundColor: color,
          cubicInterpolationMode: 'monotone'
        }
      ]
    };
    if (this.chart) {
      this.chart.data = dataConfiguration;
      this.chart.options = chartOptions;
      this.chart.update();
    } else {
      this.chart = new ChartJS(this.canvas, {
        type: 'line',
        data: dataConfiguration,
        options: chartOptions
      });
    }
    this.chart.resize();
  };

  initializeCanvas = (canvas) => {
    this.canvas = canvas;
    this.initializeChart();
  };

  render () {
    const {className} = this.props;
    return (
      <canvas
        ref={this.initializeCanvas}
        className={className}
      />
    );
  }
}

Chart.propTypes = {
  className: PropTypes.string,
  data: PropTypes.array,
  labels: PropTypes.array,
  title: PropTypes.string,
  color: PropTypes.string,
  background: PropTypes.string,
  textColor: PropTypes.string,
  lineColor: PropTypes.string,
  pending: PropTypes.bool,
  onClick: PropTypes.func
};

class UsersActivityChart extends React.Component {
  state = {
    mode: Modes.active,
    period: undefined,
    range: undefined,
    pending: false,
    error: undefined,
    tooltip: undefined
  };

  @computed
  get chartColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@primary-color'] || DEFAULT_COLOR;
    }
    return DEFAULT_COLOR;
  }

  @computed
  get backgroundColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-background-color'] ||
        DEFAULT_BACKGROUND_COLOR;
    }
    return DEFAULT_BACKGROUND_COLOR;
  }

  @computed
  get lineColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@card-border-color'] || DEFAULT_LINE_COLOR;
    }
    return DEFAULT_LINE_COLOR;
  }

  @computed
  get textColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@application-color'] || DEFAULT_TEXT_COLOR;
    }
    return DEFAULT_TEXT_COLOR;
  }

  @computed
  get errorColor () {
    const {themes} = this.props;
    if (themes && themes.currentThemeConfiguration) {
      return themes.currentThemeConfiguration['@color-red'] || DEFAULT_ERROR_COLOR;
    }
    return DEFAULT_ERROR_COLOR;
  }

  get data () {
    const {
      data = [],
      mode = Modes.active
    } = this.state;
    return data.map(o => mode === Modes.active ? o.online : o.count);
  }

  componentDidMount () {
    // noop
    this.fetchData();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {filters = {}} = this.props;
    const {
      period: currentPeriod = Period.day,
      range: currentRange,
      runner: currentRunner
    } = this.state;
    const {
      period = Period.day,
      range,
      runner
    } = filters;
    if (
      period !== currentPeriod ||
      range !== currentRange ||
      !runnersEqual(runner, currentRunner)
    ) {
      this.fetchData();
    }
  }

  fetchData = () => {
    const {filters = {}} = this.props;
    const {
      period = Period.day,
      range,
      runner
    } = filters;
    this.setState({
      pending: true,
      data: [],
      labels: [],
      period,
      range,
      runner,
      tooltip: undefined
    }, () => {
      const setError = (error) => new Promise((resolve) => {
        const {period: requestedPeriod, range: requestedRange} = this.state;
        if (requestedPeriod === period && requestedRange === range) {
          this.setState(
            {error, pending: false},
            () => resolve()
          );
        }
      });
      const setData = (data = []) => new Promise((resolve) => {
        const {period: requestedPeriod, range: requestedRange} = this.state;
        if (requestedPeriod === period && requestedRange === range) {
          const {start, end} = getPeriod(period, range);
          const ticks = generateTicks(start, end, period);
          const format = period === Period.day ? 'HH:mm' : 'YYYY-MM-DD';
          const result = ticks.map(tick => {
            const value = data
              .find(o => moment.utc(o.date).format(format) === tick.tick.utc(false).format(format));
            if (value !== undefined) {
              return {
                count: value.count,
                online: value.online,
                date: moment.utc(value.date)
              };
            }
            return {
              count: undefined,
              online: undefined,
              date: moment(tick.tick)
            };
          });
          this.setState(
            {data: result, error: undefined, pending: false, labels: ticks.map(tick => tick.label)},
            () => resolve()
          );
        }
      });
      fetchDataMock({period, range, runner})
        .then(data => setData(data))
        .catch((e) => setError(e.message));
    });
  };

  onChangeMode = (mode) => {
    const {mode: currentMode} = this.state;
    if (currentMode !== mode) {
      this.setState({
        mode
      });
    }
  }

  renderTooltipInfo = () => {
    const {data = [], tooltip, period} = this.state;
    if (tooltip) {
      const {index} = tooltip;
      const info = data[index];
      if (info) {
        const format = period === Period.day
          ? 'D MMMM YYYY, HH:mm'
          : 'D MMMM YYYY';
        const {
          date
        } = info;
        return (
          <div
            className={styles.tooltip}
          >
            <div className={styles.header}>
              Online users at {date.format(format)}
            </div>
            <div className={styles.usersList}>
              {
                ['USER1', 'USER2', 'USER3'].map(user => (
                  <UserName
                    key={user}
                    className={styles.user}
                    userName={user}
                  />
                ))
              }
            </div>
          </div>
        );
      }
    }
    return null;
  };

  onItemClick = (index, {x, y}) => {
    const {data = []} = this.state;
    const info = data[index];
    if (info) {
      this.setState({
        tooltip: {
          x,
          y,
          index
        }
      });
    }
  };

  tooltipVisibilityChange = (visible) => {
    if (!visible) {
      this.setState({
        tooltip: undefined
      });
    }
  }

  render () {
    const {
      error,
      pending,
      mode: currentMode,
      labels = [],
      tooltip
    } = this.state;
    const {
      className,
      style
    } = this.props;
    if (error) {
      return (
        <div className={className} style={style}>
          <Alert message={error} type="error" />
        </div>
      );
    }
    return (
      <div
        className={
          classNames(
            className,
            styles.container,
            'cp-panel'
          )
        }
        style={style}
      >
        <div className={styles.modeSwitcher}>
          <Button.Group>
            {
              AllModes.map(mode => (
                <Button
                  key={mode}
                  size="small"
                  type={mode === currentMode ? 'primary' : 'default'}
                  onClick={() => this.onChangeMode(mode)}
                >
                  {ModeNames[mode]}
                </Button>
              ))
            }
          </Button.Group>
        </div>
        <div className={styles.chartContainer}>
          <Chart
            className={styles.chart}
            data={this.data}
            labels={labels}
            title={Titles[currentMode]}
            color={this.chartColor}
            background={this.backgroundColor}
            lineColor={this.lineColor}
            textColor={this.textColor}
            pending={pending}
            onClick={this.onItemClick}
          />
          <div
            className={
              classNames(
                styles.popoverContainer,
                {
                  [styles.visible]: !!tooltip
                }
              )
            }
          >
            {
              tooltip && (
                <Popover
                  visible={!!tooltip}
                  onVisibleChange={this.tooltipVisibilityChange}
                  trigger={['click']}
                  content={this.renderTooltipInfo()}
                >
                  <div
                    style={{
                      top: tooltip.y,
                      left: tooltip.x,
                      position: 'absolute',
                      width: 1,
                      height: 1
                    }}
                  >
                    {'\u00A0'}
                  </div>
                </Popover>
              )
            }
          </div>
        </div>
      </div>
    );
  }
}

UsersActivityChart.propTypes = {
  className: PropTypes.func,
  style: PropTypes.object
};

export default inject('themes')(
  UsageNavigation.attach(
    observer(UsersActivityChart)
  )
);
