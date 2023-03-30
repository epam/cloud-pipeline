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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import ChartJS from 'chart.js';
import moment from 'moment-timezone';
import {Modal, Alert, Popover} from 'antd';
import classNames from 'classnames';
import PipelineRunParents from '../../../../models/pipelines/pipeline-run-parents';
import LoadingView from '../../../special/LoadingView';
import PipelineRunInfo from '../../../../models/pipelines/PipelineRunInfo';
import StatusIcon from '../../../special/run-status-icon';
import styles from './NestedRunsModal.css';

const DEFAULT_COLOR = '#108ee9';
const DEFAULT_BACKGROUND_COLOR = '#ffffff';
const DEFAULT_TEXT_COLOR = 'rgba(0, 0, 0, 0.65)';
const DEFAULT_LINE_COLOR = DEFAULT_TEXT_COLOR;

function runsArraysAreEqual (a, b) {
  const runIdA = [...new Set((a || []).map((item) => item.runId))].sort((r1, r2) => r1 - r2);
  const runIdB = [...new Set((b || []).map((item) => item.runId))].sort((r1, r2) => r1 - r2);
  if (runIdA.length !== runIdB.length) {
    return false;
  }
  for (let i = 0; i < runIdA.length; i += 1) {
    if (runIdA[i] !== runIdB[i]) {
      return false;
    }
  }
  return true;
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
      title,
      background = DEFAULT_BACKGROUND_COLOR,
      color = DEFAULT_COLOR,
      textColor = DEFAULT_TEXT_COLOR,
      lineColor = DEFAULT_LINE_COLOR,
      pending = false,
      onClick,
      dateAxisLabel
    } = this.props;
    const max = Math.max(0, ...data.map((o) => o.y)) + 1;
    let minUnit = 'second';
    let stepSize = 10;
    let minDate, maxDate;
    let shouldDisplayDate = false;
    if (data.length > 1) {
      minDate = moment(data[0].x);
      maxDate = moment(data[data.length - 1].x);
      shouldDisplayDate = moment(minDate).startOf('d') < moment(maxDate).startOf('d');
      const duration = maxDate.diff(minDate, 's');
      const MINUTE = 60;
      const HOUR = 60 * MINUTE;
      const DAY = 24 * HOUR;
      const WEEK = 7 * DAY;
      if (duration >= WEEK) {
        minUnit = 'day';
        minDate = moment(minDate).startOf('day');
        maxDate = moment(maxDate).endOf('day');
        stepSize = 1;
      } else if (duration > HOUR * 6) {
        minUnit = 'hour';
        minDate = moment(minDate).startOf('hour');
        maxDate = moment(maxDate).endOf('hour');
        stepSize = 1;
      } else if (duration > HOUR * 3) {
        minUnit = 'minute';
        minDate = moment(minDate).startOf('hour');
        maxDate = moment(maxDate).endOf('hour');
        stepSize = 30;
      } else if (duration > HOUR) {
        minUnit = 'minute';
        minDate = moment(minDate).startOf('hour');
        maxDate = moment(maxDate).endOf('hour');
        stepSize = 15;
      } else if (duration > MINUTE * 6) {
        minUnit = 'second';
        minDate = moment(minDate).startOf('minute');
        maxDate = moment(maxDate).endOf('minute');
        stepSize = 30;
      } else if (duration > MINUTE) {
        minUnit = 'second';
        stepSize = 15;
        minDate = moment(minDate).startOf('minute');
        maxDate = moment(maxDate).endOf('minute');
      } else {
        minUnit = 'second';
        stepSize = 10;
        minDate = moment(minDate).startOf('minute');
        maxDate = moment(maxDate).endOf('minute');
      }
    }
    const getDistancesToPoint = (item, x, y, xAxis, yAxis) => {
      const xx = xAxis.getPixelForValue(item.x);
      const yy = yAxis.getPixelForValue(item.y);
      return Math.sqrt((xx - x) ** 2 + (yy - y) ** 2);
    };
    const getClosestItemToPoint = (x, y, xAxis, yAxis) => {
      const closest = data
        .map((item) => ({
          item,
          distance: getDistancesToPoint(item, x, y, xAxis, yAxis)
        }))
        .sort((a, b) => b.distance - a.distance)
        .pop();
      if (closest) {
        return closest.item;
      }
      return undefined;
    };
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
          type: 'time',
          gridLines: {
            drawOnChartArea: false,
            color: lineColor,
            zeroLineColor: lineColor
          },
          ticks: {
            display: true,
            maxRotation: 45,
            fontColor: textColor,
            source: 'auto',
            min: minDate,
            max: maxDate
          },
          scaleLabel: {
            display: !!dateAxisLabel,
            labelString: dateAxisLabel,
            fontColor: textColor
          },
          offset: true,
          time: {
            stepSize,
            minUnit,
            displayFormats: {
              second: shouldDisplayDate ? 'D MMM, HH:mm:ss' : 'HH:mm:ss',
              minute: shouldDisplayDate ? 'D MMM, HH:mm' : 'HH:mm',
              hour: shouldDisplayDate ? 'D MMM, HH:mm' : 'HH:mm',
              day: 'D MMM',
              quarter: 'MMM'
            }
          }
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
            max,
            stepSize: 1
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
          const item = getClosestItemToPoint(canvasPosition.x, canvasPosition.y, xAxis, yAxis);
          if (item) {
            const x = xAxis.getPixelForValue(item.x);
            const y = yAxis.getPixelForValue(item.y);
            if (onClick) {
              onClick(item.item, {x, y});
            }
          }
        }
      },
      tooltips: {
        mode: 'nearest',
        intersect: false,
        callbacks: {
          title: function (items, o) {
            const [item] = items || [];
            if (item) {
              const {index} = item;
              const dataItem = data[index];
              if (dataItem) {
                return moment(dataItem.x).format('D MMMM, YYYY, HH:mm:ss');
              }
            }
            return undefined;
          }
        }
      }
    };
    const dataConfiguration = {
      datasets: [
        {
          label: 'Jobs',
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
    runsData: [],
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

  fetchToken = 0;

  componentDidMount () {
    if (this.props.runId) {
      this.fetchData();
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.runId !== this.props.runId) {
      this.fetchData();
    }
  }

  get data () {
    const {
      includeMasterRun = true
    } = this.props;
    const {
      runsData = []
    } = this.state;
    const timePoints = [
      ...new Set(
        runsData
          .filter((item) => includeMasterRun || !item.master)
          .map((item) => ([item.start, item.end]))
          .reduce((r, c) => ([...r, ...c]), [])
      )
    ].sort((a, b) => {
      if (a === undefined) {
        return 1;
      }
      if (b === undefined) {
        return -1;
      }
      return a - b;
    });
    return timePoints.map((timePoint) => {
      const date = timePoint || moment.utc().valueOf();
      const before = runsData
        .filter((item) => item.start < date && (!item.end || item.end >= date));
      const after = runsData
        .filter((item) => item.start <= date && (!item.end || item.end > date));
      if (runsArraysAreEqual(before, after)) {
        return [{
          timePoint: date,
          count: after.length,
          items: after
        }];
      }
      return [
        {
          timePoint: date,
          count: before.length,
          items: before
        },
        {
          timePoint: date,
          count: after.length,
          items: after
        }
      ];
    }).reduce((r, c) => ([...r, ...c]), []);
  }

  get dataPoints () {
    return this.data.map((item) => ({
      x: moment(item.timePoint).toDate(),
      y: item.count,
      item
    }));
  }

  fetchData = () => {
    const {
      runId
    } = this.props;
    this.fetchToken += 1;
    const token = this.fetchToken;
    if (runId) {
      this.setState({
        pending: true
      }, async () => {
        const state = {
          pending: false
        };
        const commit = () => {
          if (token === this.fetchToken) {
            this.setState(state);
          }
        };
        try {
          const runRequest = new PipelineRunInfo(runId);
          const request = new PipelineRunParents(runId);
          await Promise.all([
            runRequest.fetch(),
            request.fetch()
          ]);
          if (runRequest.error) {
            throw new Error(runRequest.error);
          }
          if (request.error) {
            throw new Error(request.error);
          }
          const run = runRequest.value;
          state.periodTitle = [
            moment.utc(run.startDate).format('D MMMM, YYYY HH:mm:ss'),
            run.endDate
              ? moment.utc(run.endDate).format('D MMMM, YYYY HH:mm:ss')
              : `till now`
          ].join(' - ');
          state.runsData = [
            {
              runId: run.id,
              startDate: run.startDate,
              endDate: run.endDate,
              status: run.status,
              master: true
            },
            ...(request.value || [])
          ].map((item) => {
            const {
              runId,
              status,
              startDate,
              endDate,
              master = false
            } = item;
            const start = moment.utc(startDate).startOf('s').valueOf();
            const end = endDate ? moment.utc(endDate).startOf('s').valueOf() : undefined;
            return {
              runId,
              status,
              start,
              end,
              master
            };
          });
          state.error = undefined;
        } catch (error) {
          state.error = error.message;
        } finally {
          commit();
        }
      });
    } else {
      this.setState({
        runsData: [],
        pending: false,
        error: undefined,
        periodTitle: undefined
      });
    }
  };

  onItemClick = (item, {x, y}) => {
    if (item) {
      this.setState({
        tooltip: {
          x,
          y,
          item
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

  renderTooltipInfo = () => {
    const {tooltip} = this.state;
    const {
      onRunClick
    } = this.props;
    const handleClick = (event, item) => {
      this.setState({tooltip: undefined});
      if (event) {
        event.stopPropagation();
        event.preventDefault();
      }
      if (typeof onRunClick === 'function') {
        onRunClick(item.runId);
      }
    };
    if (tooltip) {
      const {item} = tooltip;
      if (item) {
        const {
          items: nestedRuns = [],
          timePoint
        } = item;
        return (
          <div className={styles.tooltip}>
            <div className={styles.header}>
              Running jobs at {moment(timePoint).format('D MMMM YYYY, HH:mm:ss')}
            </div>
            <div className={styles.runsList}>
              {nestedRuns.map(run => (
                <a
                  key={`${run.runId}`}
                  className={
                    classNames(
                      styles.run,
                      'cp-text'
                    )
                  }
                  onClick={(event) => handleClick(event, run)}
                >
                  <StatusIcon
                    status={run.status}
                    small
                  />
                  <span
                    style={{marginLeft: 2}}
                  >
                    {run.runId}
                  </span>
                </a>
              ))}
            </div>
          </div>
        );
      }
    }
    return null;
  };

  render () {
    const {
      error,
      pending,
      periodTitle,
      tooltip,
      runsData = []
    } = this.state;
    if (error) {
      return (
        <div>
          <Alert message={error} type="error" />
        </div>
      );
    }
    if (runsData.length === 0 && pending) {
      return (
        <LoadingView />
      );
    }
    if (runsData.length === 0) {
      return null;
    }
    return (
      <div className={styles.chartContainer}>
        <Chart
          data={this.dataPoints}
          pending={pending}
          dateAxisLabel={periodTitle}
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

NestedRunsChart.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onRunClick: PropTypes.func,
  includeMasterRun: PropTypes.bool
};

NestedRunsChart.defaultProps = {
  includeMasterRun: true
};

function NestedRunsModal (props) {
  const {
    visible,
    runId,
    onCancel,
    onRunClick
  } = props;
  return (
    <Modal
      width="70%"
      title="Cluster usage"
      visible={visible}
      onCancel={onCancel}
      footer={null}
    >
      <NestedRunsChart
        onRunClick={onRunClick}
        runId={visible ? runId : undefined}
      />
    </Modal>
  );
}

NestedRunsModal.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onRunClick: PropTypes.func,
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export {NestedRunsChart};
export default NestedRunsModal;
