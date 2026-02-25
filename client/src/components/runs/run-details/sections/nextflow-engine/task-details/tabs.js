import React from 'react';
import {AreaChartOutlined, BarsOutlined, CodeOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import TaskRuntimePlainTextLogs from './runtime-data/task-runtime-plain-text-logs';
import styles from './task-details.css';
import TaskRuntimeMetrics from './runtime-data/task-runtime-metrics';
import TaskCommand from './runtime-data/task-command';

const iconStyle = {fontSize: '1.1rem'};

export const taskDetailsCommand = {
  tab: 'command',
  title: 'Command',
  icon: <CodeOutlined style={iconStyle} />,
  render: (task, props) => (
    <TaskCommand
      {...props}
      className={classNames(props?.className, styles.noPadding)}
      task={task}
    />
  )
};

export const taskDetailsRun = {
  tab: 'run',
  title: 'Run',
  icon: <CodeOutlined style={iconStyle} />,
  render: (task, props) => (
    <TaskRuntimePlainTextLogs
      {...props}
      reload={false}
      className={classNames(props?.className, styles.noPadding)}
      task={task}
      detailsType="run"
      asCommand
    />
  )
};

export const taskDetailsTrace = {
  tab: 'trace',
  title: 'Metrics',
  icon: <AreaChartOutlined style={iconStyle} />,
  render: (task, props) => (
    <TaskRuntimeMetrics
      {...props}
      className={classNames(props?.className, styles.noPadding)}
      task={task}
    />
  )
};

export const taskDetailsLog = {
  tab: 'log',
  title: 'Task Log',
  icon: <BarsOutlined style={iconStyle} />,
  render: (task, props) => (
    <TaskRuntimePlainTextLogs
      {...props}
      className={classNames(props?.className, styles.noPadding)}
      task={task}
      detailsType="log"
    />
  )
};

export function getTaskDetailsTabs (task) {
  return [
    taskDetailsCommand,
    taskDetailsTrace,
    taskDetailsLog
  ];
}
