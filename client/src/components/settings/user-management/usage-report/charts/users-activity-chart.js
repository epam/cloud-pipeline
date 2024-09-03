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
import UsageNavigation, {runnersEqual, RunnerTypes} from '../navigation';
import {getPeriod, Period} from '../../../../special/periods';
import UserName from '../../../../special/UserName';
import UserReports from '../../../../../models/user/Reports';
import displayDate from '../../../../../utils/displayDate';
import styles from './users-activity-chart.css';

const DEFAULT_COLOR = '#108ee9';
const DEFAULT_BACKGROUND_COLOR = '#ffffff';
const DEFAULT_TEXT_COLOR = 'rgba(0, 0, 0, 0.65)';
const DEFAULT_ERROR_COLOR = '#ff0000';
const DEFAULT_LINE_COLOR = 'rgba(0, 0, 0, 0.25)';

const Modes = {
  average: 'average',
  unique: 'unique'
};

const DEFAULT_MODE = [Modes.average];

const modes = {
  [Period.month]: [Modes.average, Modes.unique],
  [Period.day]: [Modes.average]
};

const ModeNames = {
  [Modes.average]: 'Average',
  [Modes.unique]: 'Unique'
};

const Titles = {
  [Modes.average]: 'Average users count',
  [Modes.unique]: 'Total unique users count'
};

function fetchData (options) {
  return new Promise((resolve, reject) => {
    const {
      period,
      range,
      runner = []
    } = options || {};
    const {start, end} = getPeriod(period, range);
    const utcDate = date => moment(date).utc().format('YYYY-MM-DD HH:mm:ss.SSS');
    const from = utcDate(start);
    const to = utcDate(end);
    const interval = period === Period.day ? 'HOURS' : 'DAYS';
    const request = new UserReports();
    const users = runner
      .filter(runner => runner.type === RunnerTypes.user)
      .map(runner => runner.id);
    const roles = runner
      .filter(runner => runner.type === RunnerTypes.group)
      .map(runner => runner.id);
    request
      .send({
        from,
        to,
        interval,
        users: users.length > 0 ? users : undefined,
        roles: roles.length > 0 ? roles : undefined
      })
      .then(() => {
        if (request.loaded) {
          const data = (request.value || [])
            .map(item => ({
              date: displayDate(item.periodStart, 'YYYY-MM-DD HH:mm:ss.SSS'),
              active: item.activeUsersCount,
              total: item.totalUsersCount,
              activeUsers: item.activeUsers || [],
              totalUsers: item.totalUsers || []
            }));
          resolve(data);
        } else {
          throw new Error(request.error || 'Error fetching users report');
        }
      })
      .catch(reject);
  });
}

function generateTicks (from, to, period) {
  const ticks = [];
  if (from < to) {
    const format = period === Period.day ? 'HH:mm' : 'D';
    const fullFormat = period === Period.day ? 'D MMMM, YYYY, HH:mm' : 'D MMMM, YYYY';
    const unit = period === Period.day ? 'hour' : 'day';
    const pushTick = tick => ticks.push({
      tick,
      label: displayDate(tick, format),
      fullLabel: displayDate(tick, fullFormat)
    });
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
      dataLabels = [],
      title,
      background = DEFAULT_BACKGROUND_COLOR,
      color = DEFAULT_COLOR,
      textColor = DEFAULT_TEXT_COLOR,
      lineColor = DEFAULT_LINE_COLOR,
      pending = false,
      onClick,
      dateAxisLabel
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
          scaleLabel: {
            display: !!dateAxisLabel,
            labelString: dateAxisLabel,
            fontColor: textColor
          },
          offset: true
        }],
        yAxes: [{
          gridLines: {
            display: !pending,
            color: lineColor,
            zeroLineColor: lineColor
          },
          ticks: {
            display: !pending,
            fontColor: textColor,
            beginAtZero: true,
            min: 0,
            suggestedMax: 3,
            step: 1
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
        intersect: false,
        callbacks: {
          title: function (items, o) {
            const [item] = items || [];
            if (item) {
              const {index} = item;
              return dataLabels[index] || labels[index];
            }
            return undefined;
          }
        }
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
  dataLabels: PropTypes.array,
  title: PropTypes.string,
  color: PropTypes.string,
  background: PropTypes.string,
  textColor: PropTypes.string,
  lineColor: PropTypes.string,
  pending: PropTypes.bool,
  onClick: PropTypes.func,
  dateAxisLabel: PropTypes.string
};

class UsersActivityChart extends React.Component {
  state = {
    mode: Modes.average,
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
      mode = Modes.average
    } = this.state;
    return data.map(o => mode === Modes.average ? o.active : o.total);
  }

  componentDidMount () {
    // noop
    this.fetchData();
    const {filters} = this.props;
    if (filters) {
      filters.addListener(this.updateFromFilters);
    }
  }

  componentWillUnmount () {
    const {filters} = this.props;
    if (filters) {
      filters.removeListener(this.updateFromFilters);
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.updateFromFilters();
  }

  updateFromFilters = () => {
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
  };

  fetchData = () => {
    const {filters = {}} = this.props;
    const {
      period = Period.day,
      range,
      runner
    } = filters;
    const periodHasModes = modes[period] && modes[period].length;
    this.setState({
      pending: true,
      data: [],
      labels: [],
      fullLabels: [],
      periodTitle: undefined,
      period,
      range,
      runner,
      tooltip: undefined,
      mode: periodHasModes ? modes[period][0] : DEFAULT_MODE
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
          const periodTitle = period === Period.day
            ? start.format('D MMMM, YYYY')
            : start.format('MMMM YYYY');
          const result = ticks.map(tick => {
            const value = data
              .find(o => moment(o.date).format(format) === tick.tick.format(format));
            if (value !== undefined) {
              return {
                total: value.total,
                active: value.active,
                activeUsers: value.activeUsers,
                totalUsers: value.totalUsers,
                date: moment.utc(value.date)
              };
            }
            return {
              active: undefined,
              total: undefined,
              date: moment(tick.tick),
              activeUsers: [],
              totalUsers: []
            };
          });
          this.setState(
            {
              data: result,
              error: undefined,
              pending: false,
              labels: ticks.map(tick => tick.label),
              fullLabels: ticks.map(tick => tick.fullLabel),
              periodTitle
            },
            () => resolve()
          );
        }
      });
      fetchData({period, range, runner})
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
    const {
      data = [],
      tooltip,
      period,
      mode
    } = this.state;
    if (tooltip) {
      const {index} = tooltip;
      const info = data[index];
      if (info) {
        const format = period === Period.day
          ? 'D MMMM YYYY, HH:mm'
          : 'D MMMM YYYY';
        const {
          date,
          activeUsers = [],
          totalUsers = []
        } = info;
        const users = mode === Modes.average ? activeUsers : totalUsers;
        return (
          <div
            className={styles.tooltip}
          >
            <div className={styles.header}>
              Online users at {date.format(format)}
            </div>
            <div className={styles.usersList}>
              {
                users.map(user => (
                  <UserName
                    key={`${user}`}
                    className={styles.user}
                    userName={`${user}`}
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
    const {data = [], period, mode} = this.state;
    const info = data[index];
    const clickIsAvalable = (
      (period === Period.month && mode !== Modes.average) ||
      period === Period.day
    );
    if (info && clickIsAvalable) {
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
      fullLabels = [],
      periodTitle,
      tooltip,
      period = Period.day
    } = this.state;
    const availableModes = modes[period] || DEFAULT_MODE;
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
              availableModes.length > 1 && availableModes.map(mode => (
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
            dateAxisLabel={periodTitle}
            dataLabels={fullLabels}
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

export default inject('users')(
  UsageNavigation.attach(
    observer(UsersActivityChart)
  )
);
