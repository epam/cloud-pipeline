import React from 'react';
import {BarsOutlined, CodeOutlined, CopyOutlined, ExportOutlined, FileTextOutlined, SettingOutlined} from '@ant-design/icons';
import {isMlflowEngine, isNextflowEngine} from '../../utilities/helpers';
import NextflowEngineTasks from '../../sections/nextflow-engine/tasks';
import RunParametersSection from '../../sections/parameters';
import MLFlowEngine from '../../sections/mlflow-engine';
import Reports from '../../sections/reports';
import RunLogsSection from '../../sections/logs';
import RunLaunchCommandSection from '../../sections/launch-command';
import InitializeWrapper from '../../sections/common/initialize-wrapper';
import preferencesStore from '../../../../../models/preferences/PreferencesLoad';
import {inject, observer} from 'mobx-react';

const iconStyle = {fontSize: '1.1rem'};

export const parametersTab = {
  tab: 'config',
  title: 'Configuration',
  icon: <SettingOutlined style={iconStyle} />,
  render: ({run}, refreshRun) => (<RunParametersSection run={run} refreshRun={refreshRun} />)
};

export const nextflowTasksTab = {
  tab: 'engine',
  title: 'Tasks',
  icon: <BarsOutlined style={iconStyle} />,
  render: ({run}) => (
    <InitializeWrapper run={run}>
      <NextflowEngineTasks run={run} />
    </InitializeWrapper>
  )
};

function NavigateToMlFlowActionRenderer ({run, preferences}) {
  const {id} = run || {};
  const {mlflow_base: mlFlowBase} = preferences.uiMlflowSettings || {};
  const mlflowEndpoint = (() => {
    if (id && mlFlowBase) {
      return `${mlFlowBase}#/cp/${id}`;
    }
    return undefined;
  })();
  if (mlflowEndpoint) {
    const onClick = (event) => {
      event.stopPropagation();
      event.preventDefault();
      window.open(mlflowEndpoint, '_blank');
    };
    return (
      <ExportOutlined style={{fontSize: 'large', 'cursor': 'pointer'}} onClick={onClick} />
    );
  }
  return null;
}

const NavigateToMlFlowAction = inject('preferences')(observer(NavigateToMlFlowActionRenderer));

export const mlflowTab = {
  tab: 'mlflow',
  title: 'MLflow',
  noPadding: true,
  action: ({run}) => (
    <NavigateToMlFlowAction run={run} />
  ),
  render: ({run}) => (
    <InitializeWrapper run={run}>
      <MLFlowEngine run={run} />
    </InitializeWrapper>
  )
};

export const reportTab = {
  tab: 'reports',
  title: 'Reports',
  icon: <CopyOutlined style={iconStyle} />,
  render: ({run}) => (
    <InitializeWrapper run={run}>
      <Reports runId={run.id} />
    </InitializeWrapper>
  )
};

export const logsTab = {
  tab: 'logs',
  title: 'Logs',
  icon: <FileTextOutlined style={iconStyle} />,
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
  icon: <CodeOutlined style={iconStyle} />,
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
