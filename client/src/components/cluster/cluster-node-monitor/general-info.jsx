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
import {observer} from 'mobx-react';
import {makeObservable} from 'mobx';
import {Alert, Button, Checkbox, DatePicker, Dropdown, message, Row} from 'antd';
import {DownOutlined, ExportOutlined} from '@ant-design/icons';
import {downloadBlob} from '../../../utils/download-blob';
import dayjs, {calendarDate, ensureDayjs, fromUnix} from '../../../utils/dayjs';
import classNames from 'classnames';
import LoadingView from '../../special/LoadingView.tsx';
import {CPUUsageChart, FileSystemUsageChart, MemoryUsageChart, NetworkUsageChart} from '../charts';
import {ResponsiveContainer} from '../charts/utilities';
import ClusterNodeUsageReport, * as usageUtilities from '../../../models/cluster/ClusterNodeUsageReport';
import ClusterUsageExportSettingsDialog from '../ClusterUsageExportSettingsDialog';
import continuousFetch from '../../../utils/continuous-fetch';
import styles from '../ClusterNode.module.css';

const MIN_CHART_SIZE = {width: 500, height: 350};
const CHART_MARGIN = 2;
const LIVE_UPDATE_INTERVAL = 5000;
const Range = {
  full: 'full',
  week: 'week',
  day: 'day',
  hour: 'hour',
};

function Divider() {
  return <div className={classNames(styles.divider, 'cp-divider', 'vertical')}>{'\u00A0'}</div>;
}

function ChartContainer({chart, containerSize, data, title, disableTooltips, ...other}) {
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
    <div className={styles.chartContainer} style={{width, height}}>
      <div
        className={classNames(styles.wrapper, 'cp-panel-card')}
        style={{
          width: chartWidth,
          height: chartHeight,
          margin: CHART_MARGIN,
        }}
      >
        <Chart
          title={title}
          data={data}
          width={chartWidth}
          height={chartHeight}
          disableTooltips={disableTooltips}
          {...other}
        />
      </div>
    </div>
  );
}

@observer
class GeneralInfoTab extends React.Component {
  state = {
    containerWidth: 0,
    containerHeight: 0,
    start: undefined,
    end: undefined,
    liveUpdate: true,
    rangeInitialized: false,
    exporting: false,
    exportWindowVisible: false,
  };

  constructor(props) {
    super(props);
    makeObservable(this, {});
  }

  componentDidMount() {
    this.initializeRange();
    this.checkWindowsBasedNode();
    this.initializeContinuousMonitorUpdate();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    this.initializeRange();
    this.checkWindowsBasedNode();
    if (prevProps.nodeName !== this.props.nodeName) {
      this.initializeContinuousMonitorUpdate();
    }
  }

  componentWillUnmount() {
    this.stopContinuousMonitorUpdate();
  }

  get windowsOS() {
    const {node} = this.props;
    if (node.loaded) {
      const {labels = {}, systemInfo = {}} = node.value || {};
      if (systemInfo.operatingSystem) {
        return /^windows$/i.test(systemInfo.operatingSystem);
      }
      return /^windows$/i.test(labels['kubernetes.io.host']);
    }
    return false;
  }

  get wholeRangeEnabled() {
    const {chartsData} = this.props;
    return !!chartsData.instanceFrom;
  }

  get lastWeekEnabled() {
    const {chartsData} = this.props;
    return (
      !chartsData.rangeEndIsFixed &&
      (!chartsData.instanceFrom ||
        dayjs.duration(chartsData.instanceTo - chartsData.instanceFrom, 'second').asSeconds() >
          dayjs.duration(1, 'week').asSeconds())
    );
  }

  get lastDayEnabled() {
    const {chartsData} = this.props;
    return (
      !chartsData.rangeEndIsFixed &&
      (!chartsData.instanceFrom ||
        dayjs.duration(chartsData.instanceTo - chartsData.instanceFrom, 'second').asSeconds() >
          dayjs.duration(1, 'day').asSeconds())
    );
  }

  get lastHourEnabled() {
    const {chartsData} = this.props;
    return (
      !chartsData.rangeEndIsFixed &&
      (!chartsData.instanceFrom ||
        dayjs.duration(chartsData.instanceTo - chartsData.instanceFrom, 'second').asSeconds() >
          dayjs.duration(1, 'hour').asSeconds())
    );
  }

  get retentionPeriodExceeded() {
    const {preferences, chartsData} = this.props;
    const {end} = this.state;
    if (end && preferences.loaded) {
      const retentionPeriod = preferences.getPreferenceValue(
        'system.resource.monitoring.stats.retention.period',
      );
      if (retentionPeriod) {
        const endOfRetentionPeriod = dayjs().subtract(retentionPeriod, 'day');
        if (
          fromUnix(end).isBefore(endOfRetentionPeriod) ||
          fromUnix(chartsData.instanceTo).isBefore(endOfRetentionPeriod)
        ) {
          return true;
        }
      }
    }

    return false;
  }

  initializeContinuousMonitorUpdate() {
    this.stopContinuousMonitorUpdate();
    const {stop, reset} = continuousFetch({
      call: () => this.invokeLiveUpdate(),
      intervalMS: LIVE_UPDATE_INTERVAL,
    });
    this.stopContinuosFetch = stop;
    this.resetContinuousFetch = reset;
  }

  stopContinuousMonitorUpdate() {
    if (this.stopContinuosFetch) {
      this.stopContinuosFetch();
    }
    this.stopContinuosFetch = undefined;
    this.resetContinuousFetch = undefined;
  }

  checkWindowsBasedNode = () => {
    if (this.windowsOS) {
      const {router, node} = this.props;
      if (node && node.loaded && router) {
        router.push(`/cluster/${node.value.name}/info`);
      }
    }
  };

  onResizeContainer = (width, height) => {
    this.setState({
      containerWidth: width,
      containerHeight: height,
    });
  };

  initializeRange = () => {
    const {rangeInitialized, start, end} = this.state;
    const {chartsData} = this.props;
    if (!rangeInitialized && chartsData?.initialized) {
      this.setState(
        {
          rangeInitialized: true,
          start: start || chartsData.from || chartsData.instanceFrom,
          end: end || chartsData.to || chartsData.instanceTo,
        },
        () => {
          if (chartsData.rangeEndIsFixed) {
            this.setLiveUpdate(false);
          }
        },
      );
    }
  };

  fetch = () => {
    const {chartsData} = this.props;
    chartsData.loadData();
    if (this.resetContinuousFetch) {
      this.resetContinuousFetch();
    }
  };

  reloadData = () => {
    const {chartsData} = this.props;
    const {followCommonRange} = chartsData;
    if (followCommonRange && chartsData.initialized) {
      chartsData.from = chartsData.correctDateToFixRange(this.state.start);
      chartsData.to = chartsData.correctDateToFixRange(this.state.end);
      this.fetch();
    }
  };

  invokeLiveUpdate = () => {
    let {start, end} = this.state;
    const {chartsData} = this.props;
    if (chartsData.pending) {
      return Promise.resolve();
    }
    chartsData.updateRange();
    start = start || chartsData.instanceFrom || dayjs().subtract(1, 'hour').unix();
    end = end || chartsData.instanceTo || dayjs().unix();
    const range = end - start;
    if (!chartsData.rangeEndIsFixed) {
      end = dayjs().unix();
    }
    start = end - range;
    chartsData.from = chartsData.correctDateToFixRange(start);
    chartsData.to = chartsData.correctDateToFixRange(end);
    return new Promise((resolve, reject) => {
      this.setState(
        {
          start: chartsData.from,
          end: chartsData.to,
        },
        () => {
          chartsData.loadData().then(() => {
            if (chartsData.networkError) {
              reject(new Error(chartsData.networkError));
            } else {
              resolve();
            }
          });
        },
      );
    });
  };

  setLiveUpdate = (e, clearEnd = false) => {
    const liveUpdate = typeof e === 'boolean' ? e : e.target.checked;
    const {chartsData} = this.props;
    if (liveUpdate) {
      chartsData.followCommonRange = true;
      this.initializeContinuousMonitorUpdate();
    } else {
      this.stopContinuousMonitorUpdate();
    }
    const state = {
      liveUpdate,
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
        chartsData.to = dayjs().unix();
        chartsData.from = dayjs().subtract(7, 'day').unix();
        break;
      case Range.day:
        chartsData.to = dayjs().unix();
        chartsData.from = dayjs().subtract(1, 'day').unix();
        break;
      case Range.hour:
        chartsData.to = dayjs().unix();
        chartsData.from = dayjs().subtract(1, 'hour').unix();
        break;
      default:
        break;
    }
    this.setState({
      start: chartsData.from,
      end: chartsData.to,
    });
    this.fetch();
  };

  onStartChanged = (start) => {
    let {end} = this.state;
    const {chartsData} = this.props;
    if (!chartsData.initialized) {
      return;
    }
    start = ensureDayjs(start);
    if (!start) {
      this.setState(
        {
          start: chartsData.instanceFrom,
        },
        this.reloadData,
      );
      return;
    }
    end = end ? fromUnix(end) : null;
    const newStart = calendarDate([
      start.year(),
      start.month(),
      start.date(),
      start.hour(),
      start.minute(),
    ]);
    if (end && (newStart.isAfter(end) || newStart.isSame(end))) {
      end = dayjs(newStart).add(1, 'day').subtract(1, 'second');
    }
    this.setState(
      {
        start: chartsData.correctDateToFixRange(newStart.unix()),
        end: end ? chartsData.correctDateToFixRange(end.unix()) : null,
      },
      this.reloadData,
    );
  };

  onEndChanged = (end) => {
    let {start} = this.state;
    const {chartsData} = this.props;
    if (!chartsData.initialized) {
      return;
    }
    end = ensureDayjs(end);
    if (!end) {
      this.setState(
        {
          end,
        },
        this.reloadData,
      );
      return;
    }
    start = start ? fromUnix(start) : null;
    const newEnd = calendarDate([end.year(), end.month(), end.date(), end.hour(), end.minute()]);
    if (start && (start.isAfter(newEnd) || start.isSame(newEnd))) {
      start = dayjs(newEnd).subtract(1, 'day').add(1, 'second');
    }
    this.setState(
      {
        start: start ? chartsData.correctDateToFixRange(start.unix()) : null,
        end: chartsData.correctDateToFixRange(newEnd.unix()),
      },
      this.reloadData,
    );
  };

  onRangeChanged = (start, end, final) => {
    const {chartsData} = this.props;
    chartsData.from = start;
    chartsData.to = end;
    this.setState({start, end, liveUpdate: false}, () => {
      if (final) {
        this.fetch();
      }
      this.setLiveUpdate(false);
    });
  };

  onFollowCommonRangeChanged = (e) => {
    const {chartsData} = this.props;
    chartsData.followCommonRange = e.target.checked;
    this.fetch();
    if (!chartsData.followCommonRange) {
      this.setLiveUpdate(false, true);
    }
  };

  disabledDate = (date) => {
    const {chartsData} = this.props;
    // Can not select days before today and today
    return (
      date &&
      chartsData.instanceFrom &&
      (date.unix() < chartsData.instanceFrom || date.valueOf() > Date.now())
    );
  };

  onExportClicked = (opts) => {
    const {key: mode} = opts || {};
    this.onExport(mode);
  };

  openExportSettingsWindow = () => {
    this.setState({
      exportWindowVisible: true,
    });
  };

  closeExportSettingsWindow = () => {
    this.setState({
      exportWindowVisible: false,
    });
  };

  navigateToRun = () => {
    const {chartsData, router} = this.props;
    if (!chartsData?.runId || !router) {
      return;
    }
    router.push(`/run/${chartsData.runId}`);
  };

  onExport = (mode, tick) => {
    if (mode && /^custom$/i.test(mode)) {
      this.openExportSettingsWindow();
      return;
    }
    this.setState({exporting: true}, async () => {
      const {chartsData} = this.props;
      const {start, end} = this.state;
      const hide = message.loading('Fetching usage report...', 0);
      try {
        const format = 'YYYY-MM-DD HH:mm:ss';
        let modeValue = mode;
        if (!modeValue) {
          modeValue = 'XLS';
        }
        let tickValue = tick;
        if (!tickValue) {
          tickValue = usageUtilities.autoDetectTickInterval(start, end);
        }
        const pipelineFile = new ClusterNodeUsageReport(
          chartsData.nodeName,
          start ? fromUnix(start).utc().format(format) : undefined,
          end ? fromUnix(end).utc().format(format) : undefined,
          tickValue,
          modeValue,
          chartsData.runId,
        );
        await pipelineFile.fetch();
        const res = pipelineFile.response;
        const checkForBlobErrors = (blob) => {
          return new Promise((resolve) => {
            const fr = new FileReader();
            fr.onload = function () {
              const status = JSON.parse(this.result)?.status?.toLowerCase();
              resolve(status === 'error');
            };
            fr.readAsText(blob);
          });
        };
        if (res.type?.includes('application/json') && res instanceof Blob) {
          checkForBlobErrors(res).then((error) =>
            error
              ? message.error('Error downloading file', 5)
              : downloadBlob(res, `report.${/^xls$/i.test(modeValue) ? 'xls' : 'csv'}`),
          );
        } else if (res) {
          downloadBlob(res, `report.${/^xls$/i.test(modeValue) ? 'xls' : 'csv'}`);
        }
      } catch (e) {
        message.error('Failed to download file', 5);
      } finally {
        hide();
        this.setState({exporting: false, exportWindowVisible: false});
      }
    });
  };

  render() {
    if (this.windowsOS || (!this.props.node.loaded && this.props.node.pending)) {
      return null;
    }
    const {chartsData} = this.props;
    if (chartsData?.error) {
      return <Alert type={'error'} title={chartsData.error} />;
    }
    if (!chartsData || !chartsData.initialized) {
      return <LoadingView />;
    }
    const {containerWidth, containerHeight, start, end, liveUpdate, exporting} = this.state;
    const commonChartProps = {
      followCommonScale: chartsData.followCommonRange,
      start: start || chartsData.from || chartsData.instanceFrom,
      end: end || chartsData.to || chartsData.instanceTo,
      containerSize: {containerWidth, containerHeight},
      padding: 5,
      onRangeChanged: this.onRangeChanged,
    };
    const availableExportIntervals = usageUtilities.getAvailableTickIntervals(start, end);
    return (
      <div
        className={styles.fullHeightContainer}
        style={{flexDirection: 'column', overflow: 'hidden', height: 'calc(100vh - 120px)'}}
      >
        <Row type={'flex'} justify={'end'} align={'middle'} style={{marginTop: 5, marginBottom: 5}}>
          {chartsData?.runId ? (
            <a style={{marginRight: 'auto', marginLeft: 3}} onClick={this.navigateToRun}>
              Showing statistics for run #{chartsData.runId}
            </a>
          ) : null}
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
            onChange={(e) => this.setLiveUpdate(e, true)}
          >
            Live update
          </Checkbox>
          <Divider />
          <Dropdown
            menu={{
              items: [
                {key: Range.full, label: 'Whole range', disabled: !this.wholeRangeEnabled},
                {key: Range.week, label: 'Last week', disabled: !this.lastWeekEnabled},
                {key: Range.day, label: 'Last day', disabled: !this.lastDayEnabled},
                {key: Range.hour, label: 'Last hour', disabled: !this.lastHourEnabled},
              ],
              onClick: this.setRange,
              style: {cursor: 'pointer'},
            }}
          >
            <Button>
              Set range <DownOutlined />
            </Button>
          </Dropdown>
          <Divider />
          <DatePicker
            format="YYYY-MM-DD HH:mm"
            placeholder="Start"
            onChange={this.onStartChanged}
            value={start ? fromUnix(start) : null}
            disabledDate={this.disabledDate}
          />
          <Divider />
          <DatePicker
            allowClear={!chartsData.rangeEndIsFixed}
            format="YYYY-MM-DD HH:mm"
            placeholder="End"
            onChange={this.onEndChanged}
            value={end ? fromUnix(end) : null}
            disabledDate={this.disabledDate}
          />
          {!this.retentionPeriodExceeded && <Divider />}
          {!this.retentionPeriodExceeded && (
            <Dropdown
              menu={{
                items: [
                  {key: 'XLS', label: 'Excel'},
                  {key: 'CSV', label: 'CSV'},
                  ...(availableExportIntervals.length > 1
                    ? [
                        {type: 'divider', key: 'divider'},
                        {key: 'custom', label: 'Configure export'},
                      ]
                    : []),
                ],
                onClick: this.onExportClicked,
                style: {cursor: 'pointer'},
              }}
            >
              <Button disabled={!start || exporting} onClick={() => this.onExportClicked()}>
                <ExportOutlined />
                Export
              </Button>
            </Dropdown>
          )}
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
            disableTooltips={this.state.exportWindowVisible}
            {...commonChartProps}
          />
          <ChartContainer
            title="Memory Usage"
            data={chartsData.memoryUsage}
            chart={MemoryUsageChart}
            disableTooltips={this.state.exportWindowVisible}
            {...commonChartProps}
          />
          <ChartContainer
            title="Network Usage"
            data={chartsData.networkUsage}
            chart={NetworkUsageChart}
            disableTooltips={this.state.exportWindowVisible}
            {...commonChartProps}
          />
          <ChartContainer
            title="File System"
            data={chartsData.fileSystemUsage}
            chart={FileSystemUsageChart}
            rangeChangeEnabled={false}
            disableTooltips={this.state.exportWindowVisible}
            {...commonChartProps}
          />
        </ResponsiveContainer>
        <ClusterUsageExportSettingsDialog
          disabled={this.state.exporting}
          visible={this.state.exportWindowVisible}
          onCancel={this.closeExportSettingsWindow}
          onExport={this.onExport}
          availableIntervals={availableExportIntervals}
        />
      </div>
    );
  }
}

export default GeneralInfoTab;
