import { fetchRun, fetchRunTasks, fetchRunLogs } from '@cloud-pipeline/api';
import type { Run, RunTask, RunLog, UserMetadata } from '@cloud-pipeline/core';
import { RunStatuses } from '@cloud-pipeline/core';
import { useState, useCallback, useEffect, useMemo } from 'react';
import { useLoadableStateWithInterval } from '../../../shared/hooks/use-loadable-state';
import { useAuthenticatedUserMetadata } from '../../../state/authentication/hooks';

const RUN_LOGS_MAIN_TASK = 'ui-run-logs-main-task';

type RunWithLogsInfo = {
  pending: boolean;
  error?: string;
  tasks?: RunTask[];
  run?: Run;
  logs?: RunLog[];
  selectedTask?: string;
  refreshRun: () => Promise<void>;
};

function determineTaskToLoad(
  run?: Run,
  tasks?: RunTask[],
  selectedTask?: string,
  userMetadata?: UserMetadata,
) {
  let taskToFetch: RunTask | undefined;
  const runLogsMainTask =
    ((userMetadata?.[RUN_LOGS_MAIN_TASK]?.value as string) || 'false')
      .toString()
      .toLowerCase() === 'true';
  const { pipelineName, podId } = run ?? {};
  const runningStatuses = [
    RunStatuses.running,
    RunStatuses.paused,
    RunStatuses.pausing,
    RunStatuses.resuming,
  ];
  if (tasks?.length) {
    // If no task is selected and there are some tasks in run -
    // we need to navigate to any of it
    taskToFetch = tasks[0];
  }
  if (runLogsMainTask) {
    // user has "ui-run-logs-main-task" set to true ("display main task by default").
    // we need to navigate to "pipeline" task (if it is finished) or "console" task,
    // if there isn't selected task
    const pipelineTaskName = pipelineName ?? podId ?? '';
    const pipelineTask = (tasks ?? []).find(
      (t) => (t.name || '').toLowerCase() === pipelineTaskName.toLowerCase(),
    );
    const consoleTask = (tasks ?? []).find(
      (t) => (t.name || '').toLowerCase() === 'console',
    );
    if (
      pipelineTask?.status &&
      !runningStatuses.includes(
        pipelineTask.status.toUpperCase() as RunStatuses,
      )
    ) {
      // run has finished "pipeline" task - we should navigate to it
      taskToFetch = pipelineTask;
    } else if (consoleTask) {
      // run doesn't have finished "pipeline" task - we should navigate to console task
      taskToFetch = consoleTask;
    }
  }
  return taskToFetch?.name ?? selectedTask;
}

export default function useRunWithLogsInfo(
  runId?: number,
  task?: string,
  intervalMS = 5000,
): RunWithLogsInfo {
  const [selectedTask, setSelectedTask] = useState<string | undefined>(task);
  const [run, setRun] = useState<Run | undefined>();
  const [tasks, setTasks] = useState<RunTask[] | undefined>();
  const [logs, setLogs] = useState<RunLog[] | undefined>();
  const [pending, setPending] = useState(false);
  //todo implement Error handling
  const [error, setError] = useState<string | undefined>();
  const userMetadata = useAuthenticatedUserMetadata();
  const refreshSelectedTask = useCallback(
    (run?: Run, tasks?: RunTask[], selectedTask?: string) => {
      const newSelectedTask = determineTaskToLoad(
        run,
        tasks,
        selectedTask,
        userMetadata,
      );
      if (newSelectedTask !== selectedTask) {
        setSelectedTask(newSelectedTask);
      }
    },
    [userMetadata],
  );
  const initialize = useCallback(async () => {
    if (!runId) {
      return;
    }
    setPending(true);
    const [run, tasks] = await Promise.all([
      fetchRun(runId),
      fetchRunTasks(runId),
    ]);
    setRun(run);
    setTasks(tasks);
    refreshSelectedTask(run, tasks, selectedTask);
    setPending(false);
  }, [refreshSelectedTask, runId, selectedTask]);
  useEffect(() => {
    if (!selectedTask) {
      void initialize();
    }
  }, [initialize, selectedTask]);
  useEffect(() => {
    refreshSelectedTask(run, tasks, selectedTask);
  }, [refreshSelectedTask, run, selectedTask, tasks]);
  const loadInfo = useCallback(
    async (task: string | undefined) => {
      if (!task || !runId || run?.status === RunStatuses.stopped) {
        return Promise.resolve();
      }
      const [runResponce, tasks, logs] = await Promise.all([
        fetchRun(runId),
        fetchRunTasks(runId),
        fetchRunLogs(runId, task),
      ]);
      setRun(runResponce);
      setTasks(tasks);
      setLogs(logs);
    },
    [run?.status, runId],
  );
  const refreshRun = useCallback(async () => {
    if (!runId) {
      return;
    }
    setPending(true);
    const run = await fetchRun(runId);
    setRun(run);
    setPending(false);
  }, [runId]);
  useLoadableStateWithInterval(intervalMS, loadInfo, selectedTask);
  return useMemo(
    () => ({
      run,
      tasks,
      logs,
      selectedTask,
      refreshRun,
      error,
      pending,
    }),
    [error, logs, pending, refreshRun, run, selectedTask, tasks],
  );
}
