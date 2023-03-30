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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import ChartJS from 'chart.js';
import moment from 'moment-timezone';
import {Modal, Alert, Popover} from 'antd';
import {Link} from 'react-router';
import displayDate from '../../../../utils/displayDate';
import classNames from 'classnames';
import styles from './NestedRunsModal.css';

const DEFAULT_COLOR = '#108ee9';
const DEFAULT_BACKGROUND_COLOR = '#ffffff';
const DEFAULT_TEXT_COLOR = 'rgba(0, 0, 0, 0.65)';
const DEFAULT_LINE_COLOR = DEFAULT_TEXT_COLOR;

const mockData = [{
  startDate: '2023-03-25 21:17:50.231',
  endDate: '2023-03-27 21:18:12.868',
  id: 40774
}, {
  startDate: '2023-03-27 15:13:58.670',
  endDate: '2023-03-28 15:13:58.670',
  id: 47216
}, {
  startDate: '2023-03-25 15:13:58.670',
  endDate: '2023-03-28 15:13:58.670',
  id: 47217
}, {
  startDate: '2023-03-26 15:13:58.670',
  endDate: '2023-03-28 15:13:58.670',
  id: 47218
}, {
  startDate: '2023-03-24 15:13:58.670',
  endDate: '2023-03-27 15:13:58.670',
  id: 47219
}];

const mockDataHours = [{
  startDate: '2023-03-24 16:13:58.670',
  endDate: '2023-03-25 10:13:58.670',
  id: 40410
}, {
  startDate: '2023-03-24 20:13:58.670',
  endDate: '2023-03-25 12:13:58.670',
  id: 47216
}, {
  startDate: '2023-03-24 18:13:58.670',
  endDate: '2023-03-25 01:13:58.670',
  id: 47218
}, {
  startDate: '2023-03-24 23:13:58.670',
  endDate: '2023-03-25 07:13:58.670',
  id: 47217
}, {
  startDate: '2023-03-24 15:13:58.670',
  endDate: '2023-03-25 15:13:58.670',
  id: 47219
}];

const UNIT = {
  d: 'day',
  h: 'hour',
  m: 'minute',
  s: 'second'
};

const FORMAT = {
  day: 'D',
  hour: 'HH:mm',
  minute: 'mm:ss',
  second: 'mm:ss'
};

const FULL_FORMAT = {
  day: 'YYYY-MM-DD',
  hour: 'YYYY-MM-DD HH:mm',
  minute: 'YYYY-MM-DD HH:mm:ss',
  second: 'YYYY-MM-DD HH:mm:ss'
};

function getStartEndUnit (runs) {
  const getStartEnd = (runs) => {
    let start, end;
    for (let i = 0; i < runs.length; i++) {
      let startDate = moment.utc(runs[i].startDate);
      let endDate = runs[i].endDate ? moment.utc(runs[i].endDate) : moment.utc();
      start = (!start || start > startDate)
        ? startDate : start;
      end = (!end || end > endDate)
        ? endDate : end;
    }
    return {start, end};
  };
  let {start, end} = getStartEnd(runs);

  const getUnit = (start, end) => {
    const units = ['d', 'h', 'm', 's'];
    let index;
    let i = 0;
    while (index === undefined) {
      if (i === (units.length - 1) ||
        end.diff(start, units[i], false) > 1
      ) {
        index = i;
      } else {
        i += 1;
      }
    }
    return UNIT[units[index || 0]];
  };
  const unit = getUnit(start, end);

  start = moment(moment.utc(start).startOf(unit).toDate());
  end = moment(moment.utc(end).endOf(unit).toDate());
  return {start, end, unit};
}

function generateTicks (from, to, unit) {
  const ticks = [];
  if (from < to) {
    const format = FORMAT[unit];
    const fullFormat = FULL_FORMAT[unit];
    const pushTick = tick => ticks.push({
      tick,
      label: displayDate(tick, format),
      fullLabel: displayDate(tick, fullFormat)
    });
    let tick = moment(from).toDate();
    while (tick <= to) {
      pushTick(tick);
      tick = moment(tick).add(1, unit).toDate();
    }
    tick = moment(tick).add(1, unit).toDate();
  }
  return ticks;
}

function generateData (ticks, data) {
  return ticks.map((tick, index) => {
    const itemData = {
      nestedRuns: [],
      total: 0,
      date: tick.tick
    };
    for (let i = 0; i < data.length; i++) {
      const {startDate, endDate, id} = data[i];
      const current = tick.tick;
      const next = index < (ticks.length - 1) ? ticks[index + 1].tick : moment.utc();
      if (
        moment.utc(startDate) < moment.utc(next) &&
        moment.utc(endDate) > moment.utc(current)
      ) {
        itemData.total += 1;
        itemData.nestedRuns.push(id);
      } else if (moment.utc(endDate) < moment.utc(current)) {
        itemData.total -= 1;
        const toDelete = itemData.nestedRuns.indexOf(id);
        if (toDelete > -1) {
          itemData.nestedRuns.splice(toDelete, 1);
        }
      }
    }
    return itemData;
  });
}

class Chart extends React.PureComponent {
  componentDidMount () {
    this.initializeChart();
  }

  componentDidUpdate () {
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
      responsive: true,
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
            step: 4
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
    return (
      <canvas
        ref={this.initializeCanvas}
      />
    );
  }
}

@inject('themes')
@observer
class NestedRunsChart extends React.Component {
  state = {
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

  componentDidMount () {
    this.setData();
  }

  fetchData = () => new Promise((resolve) => {
    resolve(mockData);
  });

  getPeriodTitle = (start, end, unit) => {
    switch (unit) {
      case 'day':
        return `${moment(start).format('MMM DD')} - ${moment(end).format('MMM DD')}`;
      case 'hour':
        return `${moment(start).format('MMM DD hh:mm')} - ${moment(end).format('MMM DD hh:mm')}`;
      case 'minute':
        return `${moment(start).format('DD hh:mm')} - ${moment(end).format('DD hh:mm')}`;
      case 'second':
        return `${moment(start).format('h:mm:ss')} - ${moment(end).format('h:mm:ss')}`;
      default:
        return `${moment(start).format('LLL')} - ${moment(end).format('LLL')}`;
    }
  };

  setData = () => {
    this.setState({
      pending: true,
      data: [],
      labels: [],
      fullLabels: [],
      periodTitle: undefined,
      tooltip: undefined
    }, () => {
      this.fetchData()
        .then((data = []) => new Promise((resolve) => {
          const {start, end, unit} = getStartEndUnit(data);
          const ticks = generateTicks(start, end, unit);
          const periodTitle = this.getPeriodTitle(start, end, unit);
          const result = generateData(ticks, data);
          this.setState(
            {
              data: result,
              dataToDraw: result.map(item => item.total),
              error: undefined,
              pending: false,
              labels: ticks.map(tick => tick.label),
              fullLabels: ticks.map(tick => tick.fullLabel),
              periodTitle
            },
            () => resolve()
          );
        }))
        .catch((e) => new Promise((resolve) => {
          this.setState(
            {
              error: e.message,
              pending: false
            },
            () => resolve()
          );
        })
        );
    });
  }

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

  destroy = (e) => {
    e.stopPropagation();
    this.setState({
      tooltip: undefined
    });
    this.props.onDestroy();
  }

  renderTooltipInfo = () => {
    const {
      data = [],
      tooltip,
      fullLabels
    } = this.state;
    if (tooltip) {
      const {index} = tooltip;
      const info = data[index];
      if (info) {
        const {nestedRuns = []} = info;
        return (
          <div className={styles.tooltip}>
            <div className={styles.header}>
              Nested runs at {fullLabels[index]}
            </div>
            <div className={styles.runsList}>
              {nestedRuns.map(run => (
                <span
                  key={`${run}`}
                  className={styles.run}
                >
                  <Link
                    key={index}
                    className={classNames('cp-run-nested-run-link', styles.runId)}
                    to={`/run/${run}`}
                    onClick={this.destroy}
                  >
                    {run}
                  </Link>
                </span>
              ))}
            </div>
          </div>
        );
      }
    }
    return null;
  };

  render () {
    const {error, dataToDraw, labels, pending, periodTitle, fullLabels, tooltip} = this.state;
    if (error) {
      return (
        <div>
          <Alert message={error} type="error" />
        </div>
      );
    }
    return (
      <div className={styles.chartContainer}>
        <Chart
          data={dataToDraw}
          labels={labels}
          pending={pending}
          dateAxisLabel={periodTitle}
          dataLabels={fullLabels}
          onClick={this.onItemClick}
          color={this.chartColor}
          background={this.backgroundColor}
          lineColor={this.lineColor}
          textColor={this.textColor}
        />
        <div
          className={classNames(
            styles.popoverContainer,
            {[styles.visible]: !!tooltip}
          )}
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
                    top: (tooltip.y),
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
    );
  }
}

export default class NestedRunsModal extends React.Component {
  render () {
    return (
      <Modal
        width="70%"
        title="Nested runs graph"
        visible={this.props.visible}
        onCancel={this.props.onClose}
        footer={null}
      >
        <NestedRunsChart
          onDestroy={this.props.onClose}
        />
      </Modal>
    );
  }
}
