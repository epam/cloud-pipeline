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
import {
  Alert,
  Button,
  Checkbox,
  DatePicker,
  Dropdown,
  Icon,
  Menu,
  message,
  Row
} from 'antd';
import FileSaver from 'file-saver';
import moment from 'moment-timezone';
import LoadingView from '../special/LoadingView';
import styles from './ClusterNode.css';
import {
  CPUUsageChart,
  FileSystemUsageChart,
  MemoryUsageChart,
  NetworkUsageChart
} from './charts';
import {ResponsiveContainer} from './charts/utilities';
import ClusterNodeUsageReport, * as usageUtilities
  from '../../models/cluster/ClusterNodeUsageReport';

const MIN_CHART_SIZE = {width: 500, height: 350};
const CHART_MARGIN = 2;
const LIVE_UPDATE_INTERVAL = 5000;
const Range = {
  full: 'full',
  week: 'week',
  day: 'day',
  hour: 'hour'
};

function Divider () {
  return (
    <div className={styles.divider}>
      {'\u00A0'}
    </div>
  );
}

function ChartContainer (
  {
    chart,
    containerSize,
    data,
    title,
    ...other
  }
) {
  const {containerWidth, containerHeight} = containerSize;
  let width = containerWidth / 2.0;
  let height = containerHeight / 2.0;
  if (width <= MIN_CHART_SIZE.width) {
    width = Math.max(containerWidth, MIN_CHART_SIZE.width);
  }
  if (height < MIN_CHART_SIZE.height) {
    height = MIN_CHART_SIZE.height;
  }
  const chartWidth = width - 2 * CHART_MARGIN;
  const chartHeight = height - 2 * CHART_MARGIN;
  const Chart = chart;
  return (
    <div
      className={styles.chartContainer}
      style={{width, height}}
    >
      <div
        className={styles.wrapper}
        style={{
          width: chartWidth,
          height: chartHeight,
          margin: CHART_MARGIN
        }}
      >
        <Chart
          title={title}
          data={data}
          width={chartWidth}
          height={chartHeight}
          {...other}
        />
      </div>
    </div>
  );
}

@inject('chartsData')
@observer
class ClusterNodeMonitor extends React.Component {
  state = {
    containerWidth: 0,
    containerHeight: 0,
    start: undefined,
    end: undefined,
    liveUpdate: true,
    rangeInitialized: false,
    exporting: false
  };

  liveUpdateTimer;

  @computed
  get wholeRangeEnabled () {
    const {chartsData} = this.props;
    return !!chartsData.instanceFrom;
  }

  @computed
  get lastWeekEnabled () {
    const {chartsData} = this.props;
    return !chartsData.rangeEndIsFixed && (
      !chartsData.instanceFrom ||
      moment.duration(chartsData.instanceTo - chartsData.instanceFrom, 's') >
      moment.duration(1, 'w')
    );
  }

  @computed
  get lastDayEnabled () {
    const {chartsData} = this.props;
    return !chartsData.rangeEndIsFixed && (
      !chartsData.instanceFrom ||
      moment.duration(chartsData.instanceTo - chartsData.instanceFrom, 's') >
      moment.duration(1, 'd')
    );
  }

  @computed
  get lastHourEnabled () {
    const {chartsData} = this.props;
    return !chartsData.rangeEndIsFixed && (
      !chartsData.instanceFrom ||
      moment.duration(chartsData.instanceTo - chartsData.instanceFrom, 's') >
      moment.duration(1, 'h')
    );
  }

  componentDidMount () {
    this.liveUpdateTimer = setInterval(
      this.invokeLiveUpdate,
      LIVE_UPDATE_INTERVAL
    );
    this.initializeRange();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.initializeRange();
  }

  componentWillUnmount () {
    if (this.liveUpdateTimer) {
      clearInterval(this.liveUpdateTimer);
      delete this.liveUpdateTimer;
    }
  }

  onResizeContainer = (width, height) => {
    this.setState({
      containerWidth: width,
      containerHeight: height
    });
  };

  initializeRange = () => {
    const {rangeInitialized, start, end} = this.state;
    const {chartsData} = this.props;
    if (!rangeInitialized && chartsData.initialized) {
      this.setState({
        rangeInitialized: true,
        start: start || chartsData.from || chartsData.instanceFrom,
        end: end || chartsData.to || chartsData.instanceTo
      }, () => {
        if (chartsData.rangeEndIsFixed) {
          this.setLiveUpdate(false);
        }
      });
    }
  };

  reloadData = () => {
    const {chartsData} = this.props;
    const {followCommonRange} = chartsData;
    if (followCommonRange && chartsData.initialized) {
      chartsData.from = chartsData.correctDateToFixRange(this.state.start);
      chartsData.to = chartsData.correctDateToFixRange(this.state.end);
      chartsData.loadData();
    }
  };

  invokeLiveUpdate = () => {
    let {start, end} = this.state;
    const {chartsData} = this.props;
    if (chartsData.pending) {
      return;
    }
    chartsData.updateRange();
    start = start || chartsData.instanceFrom || moment().add(-1, 'day').unix();
    end = end || chartsData.instanceTo || moment().unix();
    const range = end - start;
    if (!chartsData.rangeEndIsFixed) {
      end = moment().unix();
    }
    start = end - range;
    chartsData.from = chartsData.correctDateToFixRange(start);
    chartsData.to = chartsData.correctDateToFixRange(end);
    chartsData.loadData();
    this.setState({
      start: chartsData.from,
      end: chartsData.to
    });
  };

  setLiveUpdate = (e, clearEnd = false) => {
    const liveUpdate = typeof e === 'boolean' ? e : e.target.checked;
    const {chartsData} = this.props;
    if (liveUpdate) {
      chartsData.followCommonRange = true;
      if (!this.liveUpdateTimer) {
        this.liveUpdateTimer = setInterval(this.invokeLiveUpdate, LIVE_UPDATE_INTERVAL);
      }
      this.invokeLiveUpdate();
    } else if (this.liveUpdateTimer) {
      clearInterval(this.liveUpdateTimer);
      delete this.liveUpdateTimer;
    }
    const state = {
      liveUpdate
    };
    if (!liveUpdate && clearEnd) {
      state.end = undefined;
    }
    this.setState(state);
  };

  setRange = ({key}) => {
    const {chartsData} = this.props;
    chartsData.followCommonRange = true;
    switch (key) {
      case Range.full:
        chartsData.from = chartsData.instanceFrom;
        chartsData.to = chartsData.rangeEndIsFixed ? chartsData.instanceTo : undefined;
        break;
      case Range.week:
        chartsData.to = moment().unix();
        chartsData.from = moment().add(-7, 'day').unix();
        break;
      case Range.day:
        chartsData.to = moment().unix();
        chartsData.from = moment().add(-1, 'day').unix();
        break;
      case Range.hour:
        chartsData.to = moment().unix();
        chartsData.from = moment().add(-1, 'hour').unix();
        break;
      default:
        break;
    }
    this.setState({
      start: chartsData.from,
      end: chartsData.to
    });
    chartsData.loadData();
  };

  onStartChanged = (start) => {
    let {end} = this.state;
    const {chartsData} = this.props;
    if (!chartsData.initialized) {
      return;
    }
    if (!start) {
      this.setState({
        start: chartsData.instanceFrom
      }, this.reloadData);
      return;
    }
    end = end ? moment.unix(end) : null;
    const newStart = moment([
      start.get('year'),
      start.get('month'),
      start.get('date')
    ]);
    if (end && newStart >= end) {
      end = moment(newStart);
      end.add(1, 'day').add(-1, 'second');
    }
    this.setState({
      start: chartsData.correctDateToFixRange(newStart.unix()),
      end: end ? chartsData.correctDateToFixRange(end.unix()) : null
    }, this.reloadData);
  };

  onEndChanged = (end) => {
    let {start} = this.state;
    const {chartsData} = this.props;
    if (!chartsData.initialized) {
      return;
    }
    if (!end) {
      this.setState({
        end
      }, this.reloadData);
      return;
    }
    start = start ? moment.unix(start) : null;
    const newEnd = moment([
      end.get('year'),
      end.get('month'),
      end.get('date'),
      23,
      59,
      59
    ]);
    if (start && start >= newEnd) {
      start = moment(newEnd);
      start.add(-1, 'day').add(1, 'second');
    }
    this.setState({
      start: start ? chartsData.correctDateToFixRange(start.unix()) : null,
      end: chartsData.correctDateToFixRange(newEnd.unix())
    }, this.reloadData);
  };

  onRangeChanged = (start, end, final) => {
    const {chartsData} = this.props;
    chartsData.from = start;
    chartsData.to = end;
    this.setState({start, end, liveUpdate: false}, () => {
      if (final) {
        chartsData.loadData();
      }
      this.setLiveUpdate(false);
    });
  };

  onFollowCommonRangeChanged = (e) => {
    const {chartsData} = this.props;
    chartsData.followCommonRange = e.target.checked;
    chartsData.loadData();
    if (!chartsData.followCommonRange) {
      this.setLiveUpdate(false, true);
    }
  };

  disabledDate = (date) => {
    const {chartsData} = this.props;
    // Can not select days before today and today
    return date &&
      chartsData.instanceFrom &&
      (
        date.unix() < chartsData.instanceFrom ||
        date.valueOf() > Date.now()
      );
  };

  onExportClicked = (opts) => {
    let {key: tick} = opts || {};
    this.setState({exporting: true}, async () => {
      const {chartsData} = this.props;
      const {start, end} = this.state;
      const hide = message.loading('Fetching usage report...', 0);
      try {
        const format = 'YYYY-MM-DD HH:mm:ss';
        if (!tick) {
          tick = usageUtilities.autoDetectTickInterval(start, end);
        }
        const pipelineFile = new ClusterNodeUsageReport(
          chartsData.nodeName,
          start ? moment.unix(start).utc().format(format) : undefined,
          end ? moment.unix(end).utc().format(format) : undefined,
          tick
        );
        let res;
        await pipelineFile.fetch();
        res = pipelineFile.response;
        const checkForBlobErrors = (blob) => {
          return new Promise(resolve => {
            const fr = new FileReader();
            fr.onload = function () {
              const status = JSON.parse(this.result)?.status?.toLowerCase();
              resolve(status === 'error');
            };
            fr.readAsText(blob);
          });
        };
        if (res.type?.includes('application/json') && res instanceof Blob) {
          checkForBlobErrors(res)
            .then(error => error
              ? message.error('Error downloading file', 5)
              : FileSaver.saveAs(res, 'report.csv')
            );
        } else if (res) {
          FileSaver.saveAs(res, 'report.csv');
        }
      } catch (e) {
        message.error('Failed to download file', 5);
      } finally {
        hide();
        this.setState({exporting: false});
      }
    });
  };

  render () {
    const {
      chartsData
    } = this.props;
    if (chartsData.error) {
      return (
        <Alert type={'error'} message={chartsData.error} />
      );
    }
    if (!chartsData.initialized) {
      return (
        <LoadingView />
      );
    }
    const {
      containerWidth,
      containerHeight,
      start,
      end,
      liveUpdate,
      exporting
    } = this.state;
    const commonChartProps = {
      followCommonScale: chartsData.followCommonRange,
      start: start || chartsData.from || chartsData.instanceFrom,
      end: end || chartsData.to || chartsData.instanceTo,
      containerSize: {containerWidth, containerHeight},
      padding: 5,
      onRangeChanged: this.onRangeChanged
    };
    return (
      <div
        className={styles.fullHeightContainer}
        style={{flexDirection: 'column', overflow: 'hidden'}}
      >
        <Row type={'flex'} justify={'end'} align={'middle'} style={{marginTop: 5, marginBottom: 5}}>
          <Checkbox
            checked={chartsData.followCommonRange}
            onChange={this.onFollowCommonRangeChanged}
          >
            Common range for all charts
          </Checkbox>
          <Divider />
          <Checkbox
            checked={liveUpdate}
            disabled={chartsData.rangeEndIsFixed}
            onChange={e => this.setLiveUpdate(e, true)}
          >
            Live update
          </Checkbox>
          <Divider />
          <Dropdown
            overlay={(
              <Menu onClick={this.setRange}>
                <Menu.Item
                  key={Range.full}
                  disabled={!this.wholeRangeEnabled}
                >
                  Whole range
                </Menu.Item>
                <Menu.Item
                  key={Range.week}
                  disabled={!this.lastWeekEnabled}
                >
                  Last week
                </Menu.Item>
                <Menu.Item
                  key={Range.day}
                  disabled={!this.lastDayEnabled}
                >
                  Last day
                </Menu.Item>
                <Menu.Item
                  key={Range.hour}
                  disabled={!this.lastHourEnabled}
                >
                  Last hour
                </Menu.Item>
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
            onChange={this.onStartChanged}
            value={start ? moment.unix(start) : undefined}
            disabledDate={this.disabledDate}
          />
          <Divider />
          <DatePicker
            allowClear={!chartsData.rangeEndIsFixed}
            format="YYYY-MM-DD HH:mm"
            placeholder="End"
            onChange={this.onEndChanged}
            value={end ? moment.unix(end) : undefined}
            disabledDate={this.disabledDate}
          />
          <Divider />
          <Dropdown
            overlay={(
              <Menu onClick={this.onExportClicked}>
                {
                  usageUtilities
                    .getAvailableTickIntervals(start, end)
                    .map((unit) => (
                      <Menu.Item key={unit.value}>
                        Interval: {unit.name}
                      </Menu.Item>
                    ))
                }
              </Menu>
            )}>
            <Button
              disabled={!start || exporting}
              onClick={() => this.onExportClicked()}
            >
              <Icon type="export" />Export
            </Button>
          </Dropdown>
        </Row>
        <ResponsiveContainer
          className={styles.fullHeightContainer}
          onResize={this.onResizeContainer}
          style={{overflow: 'auto'}}
        >
          <ChartContainer
            title="CPU Usage"
            data={chartsData.cpuUsage}
            chart={CPUUsageChart}
            {...commonChartProps}
          />
          <ChartContainer
            title="Memory Usage"
            data={chartsData.memoryUsage}
            chart={MemoryUsageChart}
            {...commonChartProps}
          />
          <ChartContainer
            title="Network Usage"
            data={chartsData.networkUsage}
            chart={NetworkUsageChart}
            {...commonChartProps}
          />
          <ChartContainer
            title="File System"
            data={chartsData.fileSystemUsage}
            chart={FileSystemUsageChart}
            rangeChangeEnabled={false}
            {...commonChartProps}
          />
        </ResponsiveContainer>
      </div>
    );
  }
}

export default ClusterNodeMonitor;
