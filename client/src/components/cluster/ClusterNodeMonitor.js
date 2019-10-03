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
import {
  Alert,
  Button,
  Checkbox,
  DatePicker,
  Dropdown,
  Icon,
  Menu,
  Row
} from 'antd';
import moment from 'moment';
import LoadingView from '../special/LoadingView';
import styles from './ClusterNode.css';
import {
  CPUUsageChart,
  FileSystemUsageChart,
  MemoryUsageChart,
  NetworkUsageChart
} from './charts';
import {ResponsiveContainer} from './charts/utilities';

const MIN_CHART_SIZE = {width: 500, height: 350};
const CHART_MARGIN = 2;
const LIFE_UPDATE_INTERVAL = 5000;
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

@inject('node', 'chartsData')
@observer
class ClusterNodeMonitor extends React.Component {
  state = {
    containerWidth: 0,
    containerHeight: 0,
    start: undefined,
    end: undefined,
    lifeUpdate: true
  };

  lifeUpdateTimer;

  componentDidMount () {
    this.lifeUpdateTimer = setInterval(
      this.invokeLifeUpdate,
      LIFE_UPDATE_INTERVAL
    );
  }

  componentWillUnmount () {
    if (this.lifeUpdateTimer) {
      clearInterval(this.lifeUpdateTimer);
      delete this.lifeUpdateTimer;
    }
  }

  onResizeContainer = (width, height) => {
    this.setState({
      containerWidth: width,
      containerHeight: height
    });
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

  invokeLifeUpdate = () => {
    let {start, end} = this.state;
    const {chartsData} = this.props;
    if (chartsData.pending) {
      return;
    }
    start = start || chartsData.instanceFrom || moment().add(-1, 'day').unix();
    end = end || chartsData.instanceTo || moment().unix();
    const range = end - start;
    end = moment().unix();
    start = end - range;
    chartsData.from = chartsData.correctDateToFixRange(start);
    chartsData.to = chartsData.correctDateToFixRange(end);
    chartsData.loadData();
    this.setState({
      start: chartsData.from,
      end: chartsData.to
    });
  };

  setLifeUpdate = (e) => {
    const lifeUpdate = e.target.checked;
    const {chartsData} = this.props;
    if (lifeUpdate) {
      chartsData.followCommonRange = true;
      if (!this.lifeUpdateTimer) {
        this.lifeUpdateTimer = setInterval(this.invokeLifeUpdate, LIFE_UPDATE_INTERVAL);
      }
      this.invokeLifeUpdate();
    } else if (this.lifeUpdateTimer) {
      clearInterval(this.lifeUpdateTimer);
      delete this.lifeUpdateTimer;
    }
    this.setState({
      lifeUpdate
    });
  };

  setRange = ({key}) => {
    const {chartsData} = this.props;
    chartsData.followCommonRange = true;
    switch (key) {
      case Range.full:
        chartsData.from = chartsData.instanceFrom;
        chartsData.to = undefined;
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
    this.setState({start, end, lifeUpdate: false}, () => {
      if (final) {
        chartsData.loadData();
      }
      this.setLifeUpdate({target: {checked: false}});
    });
  };

  onFollowCommonRangeChanged = (e) => {
    const {chartsData} = this.props;
    chartsData.followCommonRange = e.target.checked;
    chartsData.loadData();
    if (!chartsData.followCommonRange) {
      this.setLifeUpdate({target: {checked: false}});
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
      lifeUpdate
    } = this.state;
    const commonChartProps = {
      followCommonScale: chartsData.followCommonRange,
      start: start || chartsData.instanceFrom,
      end,
      containerSize: {containerWidth, containerHeight},
      padding: 5,
      onRangeChanged: this.onRangeChanged
    };
    return (
      <div
        className={styles.fullHeightContainer}
        style={{flexDirection: 'column', overflow: 'hidden'}}
      >
        <Row type={'flex'} justify={'end'} align={'middle'} style={{margin: 5}}>
          <Checkbox
            checked={chartsData.followCommonRange}
            onChange={this.onFollowCommonRangeChanged}
          >
            Common range for all charts
          </Checkbox>
          <Divider />
          <Checkbox
            checked={lifeUpdate}
            onChange={this.setLifeUpdate}
          >
            Life update
          </Checkbox>
          <Divider />
          <Dropdown
            overlay={(
              <Menu onClick={this.setRange}>
                <Menu.Item key={Range.full}>Whole range</Menu.Item>
                <Menu.Item key={Range.week}>Last week</Menu.Item>
                <Menu.Item key={Range.day}>Last day</Menu.Item>
                <Menu.Item key={Range.hour}>Last hour</Menu.Item>
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
            format="YYYY-MM-DD HH:mm"
            placeholder="End"
            onChange={this.onEndChanged}
            value={end ? moment.unix(end) : undefined}
            disabledDate={this.disabledDate}
          />
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
