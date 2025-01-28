import React from 'react';
import {isNextflowEngine} from '../../utilities/helpers';
import NextflowEngineTasks from '../../sections/nextflow-engine/tasks';
import RunParametersSection from '../../sections/parameters';
import RunLogsSection from '../../sections/logs';

export const parametersTab = {
  tab: 'parameters',
  title: 'Parameters',
  render: ({run}) => (<RunParametersSection run={run} />)
};

export const nextflowTasksTab = {
  tab: 'nextflow',
  title: 'Tasks',
  render: ({run}) => (<NextflowEngineTasks run={run} />)
};

export const logsTab = {
  tab: 'logs',
  title: 'Logs',
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
