import { fetchRunEngineStats, fetchRunEngineTasks } from '@cloud-pipeline/api';
import type { EngineTasks, RunTasksData } from '@cloud-pipeline/core';
import { useState, useEffect, useCallback } from 'react';
import { TASKS_PAGE_SIZE } from '../constants';
import type { SortingState } from '../types';

export const useRunEngineTasks = (runId?: number) => {
  const [taskStats, setTaskStats] = useState<EngineTasks>({});
  const [tasks, setTasks] = useState<RunTasksData['elements']>([]);
  const [isStatsLoading, setIsStatsLoading] = useState(false);
  const [isTasksLoading, setIsTasksLoading] = useState(false);
  const [statsError, setStatsError] = useState<Error | null>(null);
  const [tasksError, setTasksError] = useState<Error | null>(null);
  const [tasksCount, setTasksCount] = useState(0);

  const fetchTasks = useCallback(
    async (selectedTask?: string, page = 1, sortingState?: SortingState) => {
      if (!runId) {
        return;
      }

      const sorting = sortingState?.column
        ? {
            column: sortingState.column,
            descending: sortingState.order === 'descend',
          }
        : undefined;

      setIsTasksLoading(true);

      try {
        const tasks = await fetchRunEngineTasks({
          runId,
          filter: { taskGroup: selectedTask, pageSize: TASKS_PAGE_SIZE, page, sorting },
        });
        setTasks(tasks.elements);
        setTasksCount(tasks.totalCount);
      } catch (err) {
        setTasksError(err instanceof Error ? err : new Error('Error fetching run tasks'));
      } finally {
        setIsTasksLoading(false);
      }
    },
    [runId],
  );

  useEffect(() => {
    const fetchTasksInfo = async () => {
      if (!runId) {
        return;
      }

      setIsStatsLoading(true);
      setStatsError(null);

      try {
        const [taskStats] = await Promise.all([fetchRunEngineStats(runId), fetchTasks()]);
        setTaskStats(taskStats);
      } catch (err) {
        setStatsError(err instanceof Error ? err : new Error('Error fetching run stats'));
      } finally {
        setIsStatsLoading(false);
      }
    };

    void fetchTasksInfo();
  }, [fetchTasks, runId]);

  return {
    isTasksLoading,
    isStatsLoading,
    tasksError,
    statsError,
    tasks,
    taskStats,
    tasksCount,
    fetchTasks,
  };
};
