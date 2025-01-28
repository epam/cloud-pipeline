import React from 'react';
import {Icon} from 'antd';
import {isNextflowEngine} from '../../utilities/helpers';
import NextflowEngineTasks from '../../sections/nextflow-engine/tasks';
import RunParametersSection from '../../sections/parameters';
import RunLogsSection from '../../sections/logs';

const iconStyle = {fontSize: '1.1rem'};

export const parametersTab = {
  tab: 'parameters',
  title: 'Parameters',
  icon: <Icon type="setting" style={iconStyle} />,
  render: ({run}) => (<RunParametersSection run={run} />)
};

export const nextflowTasksTab = {
  tab: 'nextflow',
  title: 'Tasks',
  icon: <Icon type="bars" style={iconStyle} />,
  render: ({run}) => (<NextflowEngineTasks run={run} />)
};

export const logsTab = {
  tab: 'logs',
  title: 'Logs',
  icon: <Icon type="code-o" style={iconStyle} />,
  render: ({run, runTasks, pending}) => (
    <RunLogsSection
      run={run}
      runTasks={runTasks}
      pending={pending}
    />
  )
};

export function getRunTabs (run) {
  if (!run) {
    return [logsTab];
  }
  const tabs = [];
  if (isNextflowEngine(run)) {
    tabs.push(nextflowTasksTab);
  }
  tabs.push(parametersTab);
  tabs.push(logsTab);
  return tabs;
}
