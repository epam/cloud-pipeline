import React from 'react';
import {Icon} from 'antd';
import {isMlflowEngine, isNextflowEngine} from '../../utilities/helpers';
import NextflowEngineTasks from '../../sections/nextflow-engine/tasks';
import RunParametersSection from '../../sections/parameters';
import MLFlowEngine from '../../sections/mlflow-engine';
import Reports from '../../sections/reports';
import RunLogsSection from '../../sections/logs';
import RunLaunchCommandSection from '../../sections/launch-command';
import InitializeWrapper from '../../sections/common/initialize-wrapper';
import preferencesStore from "../../../../../models/preferences/PreferencesLoad";

const iconStyle = {fontSize: '1.1rem'};

export const parametersTab = {
  tab: 'config',
  title: 'Configuration',
  icon: <Icon type="setting" style={iconStyle} />,
  render: ({run}) => (<RunParametersSection run={run} />)
};

export const nextflowTasksTab = {
  tab: 'engine',
  title: 'Tasks',
  icon: <Icon type="bars" style={iconStyle} />,
  render: ({run}) => (
    <InitializeWrapper run={run}>
      <NextflowEngineTasks run={run} />
    </InitializeWrapper>
  )
};

export const mlflowTab = {
  tab: 'mlflow',
  title: 'MLflow',
  noPadding: true,
  render: ({run}) => (
    <InitializeWrapper run={run}>
      <MLFlowEngine run={run} />
    </InitializeWrapper>
  )
};

export const reportTab = {
  tab: 'reports',
  title: 'Reports',
  icon: <Icon type="copy" style={iconStyle} />,
  render: ({run}) => (
    <InitializeWrapper run={run}>
      <Reports runId={run.id} />
    </InitializeWrapper>
  )
};

export const logsTab = {
  tab: 'logs',
  title: 'Logs',
  icon: <Icon type="file-text" style={iconStyle} />,
  render: ({run, runTasks, pending, loaded}) => (
    <RunLogsSection
      run={run}
      runTasks={runTasks}
      pending={pending}
      loaded={loaded}
    />
  )
};

export const launchCommandTab = {
  tab: 'launchCommand',
  title: 'Launch Command',
  icon: <Icon type="code-o" style={iconStyle} />,
  render: (
    {
      run,
      pending,
      error,
      loaded,
      cliCommands,
      cliCommandsPending,
      cliCommandsError,
      runPayload
    }
  ) => (
    <RunLaunchCommandSection
      run={run}
      pending={pending || cliCommandsPending}
      error={cliCommandsError || error}
      loaded={loaded}
      linuxCode={cliCommands ? cliCommands.linux : undefined}
      windowsCode={cliCommands ? cliCommands.windows : undefined}
      runPayload={runPayload}
    />
  ),
  asPanel: false
};

export function getRunTabs (run, preferences = preferencesStore) {
  if (!run) {
    return [logsTab];
  }

  const mlFlowSettings = preferences.uiMlflowSettings;

  const tabs = [];

  if (isNextflowEngine(run)) {
    tabs.push(nextflowTasksTab);
    tabs.push(parametersTab);
    tabs.push(logsTab);
    tabs.push(reportTab);
  } else if (isMlflowEngine(run) && mlFlowSettings && mlFlowSettings.mlflow_base) {
    tabs.push(mlflowTab);
    tabs.push(logsTab);
    tabs.push(parametersTab);
  } else {
    tabs.push(logsTab);
    tabs.push(parametersTab);
  }

  tabs.push(launchCommandTab);

  return tabs;
}
