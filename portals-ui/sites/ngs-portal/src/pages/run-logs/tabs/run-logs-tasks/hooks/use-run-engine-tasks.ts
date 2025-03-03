import { fetchRunEngineTasks } from '@cloud-pipeline/api';
import type { EngineTasks } from '@cloud-pipeline/core';
import { useState, useEffect } from 'react';

export const useRunEngineTasks = (runId?: number) => {
  const [tasks, setRunTasks] = useState<EngineTasks>({});
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const fetchFiles = async () => {
      if (!runId) {
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        const runTasks = await fetchRunEngineTasks(runId);
        setRunTasks(runTasks);
      } catch (err) {
        setError(err instanceof Error ? err : new Error('Error fetching run tasks'));
      } finally {
        setIsLoading(false);
      }
    };

    void fetchFiles();
  }, [runId]);

  return {
    isTasksLoading: isLoading,
    tasksError: error,
    tasks,
  };
};
