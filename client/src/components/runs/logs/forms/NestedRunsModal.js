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
import moment from 'moment-timezone';
import {Modal, Alert, Popover} from 'antd';
import classNames from 'classnames';
import PipelineRunParents from '../../../../models/pipelines/pipeline-run-parents';
import LoadingView from '../../../special/LoadingView';
import PipelineRunInfo from '../../../../models/pipelines/PipelineRunInfo';
import StatusIcon from '../../../special/run-status-icon';
import displayDate from '../../../../utils/displayDate';
import styles from './NestedRunsModal.css';
import TimelineChart from '../../../special/timeline-chart';

function buildDatasets (runsData = []) {
  if (runsData.length === 0) {
    return [];
  }
  let head = {
    next: undefined,
    items: [],
    runningItems: []
  };
  const findOrInsertNode = (timePoint, from = undefined) => {
    let _from = from;
    if (!_from && head.timePoint > timePoint) {
      const newHead = {
        timePoint: timePoint,
        next: head,
        items: [],
        runningItems: []
      };
      head = newHead;
      return newHead;
    }
    if (!_from) {
      _from = head;
    }
    let iteration = 0;
    // while (iteration < runsData.length * 2)
    // This is a safety check (number of total iterations should not be
    // more than total time points; each run has 2 time points - start and end)
    while (iteration < runsData.length * 2) {
      if (!_from.timePoint) {
        _from.timePoint = timePoint;
      }
      if (_from.timePoint === timePoint) {
        return _from;
      }
      if (!_from.next || _from.next.timePoint > timePoint) {
        const newNext = {
          timePoint: timePoint,
          next: _from.next,
          items: _from.runningItems.slice(),
          runningItems: _from.runningItems.slice()
        };
        _from.next = newNext;
        return newNext;
      }
      _from = _from.next;
      iteration += 1;
    }
    return undefined;
  };
  const now = moment().unix();
  for (let i = 0; i < runsData.length; i += 1) {
    const run = runsData[i];
    let start = findOrInsertNode(run.start);
    const end = findOrInsertNode(run.end || now, start);
    if (!start || !end) {
      break;
    }
    if (!run.end) {
      end.runningItems.push(run);
    }
    end.items.push(run);
    while (start && start !== end) {
      start.items.push(run);
      start.runningItems.push(run);
      start = start.next;
    }
  }
  const result = [{
    date: head.timePoint,
    items: [],
    value: 0
  }, {
    date: head.timePoint,
    items: head.runningItems,
    value: head.runningItems.length
  }];
  let prevItems = head.runningItems;
  head = head.next;
  while (head) {
    if (prevItems.length !== head.runningItems.length) {
      result.push({
        date: head.timePoint,
        items: prevItems,
        value: prevItems.length
      });
    }
    result.push({
      date: head.timePoint,
      items: head.runningItems,
      value: head.runningItems.length
    });
    prevItems = head.runningItems;
    head.items = undefined;
    head.runningItems = undefined;
    head = head.next;
  }
  return [{
    data: result
  }];
}

function getHoveredElementsInfo (hoveredItems = [], styles = {}) {
  const dates = [...new Set(hoveredItems.map((item) => item.item.date))];
  const renderInfoForDate = (date) => {
    const elements = hoveredItems.filter((item) => item.item.date === date);
    const color = elements.length > 0 ? elements[0].color : '#ffffff';
    const values = elements.map((item) => item.item.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    return (
      <div
        className={styles.row}
        key={`element-${date}`}
      >
        {
          color && (
            <div
              className={styles.color}
              style={{background: color}}
            />
          )
        }
        <span className={styles.dataset}>
          Jobs:
        </span>
        <span className={styles.value}>
          {min === max ? min : `${min} - ${max}`}
        </span>
      </div>
    );
  };
  return (
    <div>
      {
        dates.map((date) => (
          <div key={`${date}`}>
            <div>
              <b>{moment.unix(date).format('D MMMM YYYY, HH:mm:ss')}</b>
            </div>
            <div>
              {renderInfoForDate(date)}
            </div>
          </div>
        ))
      }
    </div>
  );
}

@inject('themes')
@observer
class NestedRunsChart extends React.Component {
  state = {
    pending: false,
    error: undefined,
    datasets: [],
    tooltip: undefined
  };

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
            displayDate(run.startDate, 'D MMMM, YYYY HH:mm:ss'),
            run.endDate
              ? displayDate(run.endDate, 'D MMMM, YYYY HH:mm:ss')
              : `till now`
          ].join(' - ');

          const runsData = [
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
            const start = moment.utc(startDate).startOf('s').unix();
            const end = endDate ? moment.utc(endDate).startOf('s').unix() : undefined;
            return {
              runId,
              status,
              start,
              end,
              master
            };
          });
          state.datasets = buildDatasets(runsData);
          state.error = undefined;
        } catch (error) {
          console.warn(error.message);
          state.error = error.message;
        } finally {
          commit();
        }
      });
    } else {
      this.setState({
        datasets: [],
        pending: false,
        error: undefined,
        periodTitle: undefined
      });
    }
  };

  onItemClick = (datasetItem, {x, y}) => {
    if (datasetItem) {
      this.setState({
        tooltip: {
          x,
          y,
          item: datasetItem.item
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
          date
        } = item;
        return (
          <div className={styles.tooltip}>
            <div className={styles.header}>
              Running jobs at {moment.unix(date).format('D MMMM YYYY, HH:mm:ss')}
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
      tooltip,
      datasets = [],
      periodTitle
    } = this.state;
    if (error) {
      return (
        <div>
          <Alert message={error} type="error" />
        </div>
      );
    }
    if (datasets.length === 0 && pending) {
      return (
        <LoadingView />
      );
    }
    if (datasets.length === 0) {
      return null;
    }
    return (
      <div className={styles.chartContainer}>
        <TimelineChart
          className={styles.chart}
          datasets={datasets}
          onItemClick={this.onItemClick}
          hover={
            tooltip ? false : {
              getHoveredElementsInfo
            }
          }
        />
        {
          periodTitle && (
            <div className={styles.chartTimelineTitle}>
              {periodTitle}
            </div>
          )
        }
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
                placement="left"
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
  onRunClick: PropTypes.func
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
