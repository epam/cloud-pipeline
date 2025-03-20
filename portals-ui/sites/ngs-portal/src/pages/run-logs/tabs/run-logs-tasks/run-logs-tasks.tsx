import type { CommonProps } from '@cloud-pipeline/components';
import { type Run } from '@cloud-pipeline/core';
import { useCallback, useMemo, useState } from 'react';
import { calculateStatusTotals } from './helpers';
import { PageSpinner } from '../../../../shared/ui';
import { useRunEngineTasks } from './hooks';
import { TasksTable, StatusTasks, TaskProgress } from './components';
import { orderedStatuses, TASKS_PAGE_SIZE } from './constants';
import type { SortingState } from './types';

type Props = CommonProps & {
  run?: Run;
};

export const RunLogsTasksTab = ({ run }: Props) => {
  const [selectedTask, setSelectedTask] = useState<string>();
  const [sorting, setSorting] = useState<SortingState | undefined>();

  const {
    isTasksLoading,
    isStatsLoading,
    taskStats,
    tasks,
    tasksError,
    statsError,
    tasksCount,
    runTasksAttributesColumns,
    fetchTasks,
  } = useRunEngineTasks(run?.id);

  const [activePage, setActivePage] = useState(1);

  const pagination = { page: activePage, pageSize: TASKS_PAGE_SIZE, total: tasksCount };

  const handleChangeSorting = useCallback(
    (sorting?: SortingState) => {
      setSorting(sorting);
      void fetchTasks(selectedTask, activePage, sorting);
    },
    [activePage, fetchTasks, selectedTask],
  );

  const handleSelectTask = useCallback(
    (task?: string) => {
      setSelectedTask(task);
      setActivePage(1);
      void fetchTasks(task, 1, sorting);
    },
    [fetchTasks, sorting],
  );

  const handleSelectPage = useCallback(
    (page: number) => {
      setActivePage(page);
      void fetchTasks(selectedTask, page, sorting);
    },
    [fetchTasks, selectedTask, sorting],
  );

  const { totalTasks, totalsByStatus, totalsByTask } = useMemo(() => {
    return taskStats ? calculateStatusTotals(taskStats) : { totalTasks: 0, totalsByStatus: {}, totalsByTask: {} };
  }, [taskStats]);

  if (isStatsLoading) {
    return <PageSpinner />;
  }

  if (statsError) {
    return <div>Error: {statsError.message}</div>;
  }

  if (!taskStats || Object.keys(taskStats).length === 0) return <div>No tasks found.</div>;

  const total = selectedTask ? (totalsByTask[selectedTask] ?? 0) : totalTasks;

  return (
    <div className="h-full overflow-auto flex flex-col">
      <div className="flex gap-4">
        <div className="flex-1">
          <h3 className="font-bold">Tasks statuses</h3>

          <div className="max-h-[186px] overflow-scroll">
            {Object.entries(taskStats).map(([taskName, statuses]) => (
              <TaskProgress
                key={taskName}
                total={totalsByTask[taskName] ?? 0}
                taskName={taskName}
                isSelected={selectedTask === taskName}
                onTaskSelect={handleSelectTask}
                statuses={statuses}
              />
            ))}
          </div>
        </div>

        <div className="flex-1">
          <h3 className="font-bold">Tasks statuses</h3>

          <div className="flex flex-col gap-1 mt-2">
            {orderedStatuses.map((status) => {
              const statusTotal = selectedTask
                ? (taskStats[selectedTask]?.[status] ?? 0)
                : (totalsByStatus[status] ?? 0);

              const percentage = total > 0 ? (statusTotal / total) * 100 : 0;

              return <StatusTasks key={status} statusTotal={statusTotal} percentage={percentage} status={status} />;
            })}
          </div>
        </div>
      </div>

      {!!tasks.length && (
        <TasksTable
          dynamicColumns={runTasksAttributesColumns}
          data={tasks}
          onPageSelect={handleSelectPage}
          className="mt-4 grow"
          isLoading={isTasksLoading}
          pagination={pagination}
          error={tasksError?.message}
          onSortChange={handleChangeSorting}
          sorting={sorting}
        />
      )}
    </div>
  );
};
