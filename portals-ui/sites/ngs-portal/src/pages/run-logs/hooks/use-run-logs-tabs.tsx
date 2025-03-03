import { useMemo, useCallback, useEffect, useState } from 'react';
import type { Run, RunLog } from '@cloud-pipeline/core';
import { useNavigate, useParams } from 'react-router-dom';
import { generateRunLogsRoutePath, RunLogsTabs } from '../../../shared/constants/routes';
import { RunLogsTab } from '../tabs/run-logs-tab';
import { RunLogsParametersTab } from '../tabs/run-logs-parameters';
import { fetchRunTasks } from '@cloud-pipeline/api';
import type { RunTasksState } from '../../../shared/hooks/use-run-tasks';
import { RunLogsTasksTab } from '../tabs/run-logs-tasks/run-logs-tasks';
import { isNextflowEngine } from '../../../shared/helpers';

export const useRunTasks = (runId: string | number | undefined): RunTasksState & { refresh: () => Promise<void> } => {
  const [state, setState] = useState<RunTasksState>({
    pending: true,
    error: undefined,
    tasks: undefined,
  });
  const refresh = useCallback(async () => {
    try {
      setState((curr) => ({
        ...curr,
        pending: true,
        error: undefined,
      }));
      const tasks = await fetchRunTasks(Number(runId));
      setState({
        pending: false,
        error: undefined,
        tasks,
      });
    } catch (err) {
      const errorText = err instanceof Error ? err.message : `Failed to load run ${runId} tasks.`;
      setState({
        pending: false,
        error: errorText,
        tasks: undefined,
      });
    }
  }, [runId]);
  useEffect(() => {
    if (runId !== undefined) {
      void refresh();
    }
  }, [refresh, runId]);
  return useMemo(
    () => ({
      ...state,
      refresh,
    }),
    [refresh, state],
  );
};

export const useRunLogsTabs = (run?: Run, logs?: RunLog[]) => {
  const { tabId } = useParams();
  const navigate = useNavigate();

  const handleChangeTab = useCallback(
    (key: string) => {
      if (run) {
        navigate(generateRunLogsRoutePath(run.id, key as RunLogsTabs));
      }
    },
    [navigate, run],
  );

  const tabs = useMemo(() => {
    const defaultTabs = [
      {
        key: RunLogsTabs.Logs,
        label: <span className="px-4">Logs</span>,
        content: run ? <RunLogsTab logs={logs} /> : null,
      },
      {
        key: RunLogsTabs.Parameters,
        label: <span className="px-4">Parameters</span>,
        content: <RunLogsParametersTab run={run} />,
      },
    ];

    if (!isNextflowEngine(run)) {
      return defaultTabs;
    }

    return [
      ...defaultTabs,
      {
        key: RunLogsTabs.Tasks,
        label: <span className="px-4">Tasks</span>,
        content: <RunLogsTasksTab run={run} />,
      },
    ];
  }, [logs, run]);

  useEffect(() => {
    if (!run) {
      return;
    }

    const isValidTab = !tabId || tabs.find(({ key }) => key === (tabId as RunLogsTabs));

    if (!isValidTab) {
      navigate(generateRunLogsRoutePath(run.id, RunLogsTabs.Logs));
    }
  }, [navigate, run, run?.id, tabId, tabs]);

  const activeTab = useMemo(() => tabs.find((tab) => tab.key === tabId) ?? tabs[0], [tabId, tabs]);

  return {
    activeTab,
    tabs,
    handleChangeTab,
  };
};
