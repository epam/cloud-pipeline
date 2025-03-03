import type { CommonProps } from '@cloud-pipeline/components';
import { type Run } from '@cloud-pipeline/core';
import { useMemo, useState } from 'react';
import { calculateStatusTotals } from '../../helpers/calculateStatusTotals';
import { StatusTasks } from './status-tasks';
import { TaskProgress } from './task-progress';
import { PageSpinner } from '../../../../shared/ui';
import { useRunEngineTasks } from './hooks/use-run-engine-tasks';
import { orderedStatuses } from './constants';

type Props = CommonProps & {
  run?: Run;
};

export const RunLogsTasksTab = ({ run }: Props) => {
  const [selectedTask, setSelectedTask] = useState<string | null>(null);

  const { isTasksLoading, tasks, tasksError } = useRunEngineTasks(run?.id);

  const { totalTasks, totalsByStatus, totalsByTask } = useMemo(() => {
    return tasks ? calculateStatusTotals(tasks) : { totalTasks: 0, totalsByStatus: {}, totalsByTask: {} };
  }, [tasks]);

  if (isTasksLoading) {
    return <PageSpinner />;
  }

  if (tasksError) {
    return <div>Error: {tasksError.message}</div>;
  }

  if (!tasks || Object.keys(tasks).length === 0) return <div>No tasks found.</div>;

  const total = selectedTask ? (totalsByTask[selectedTask] ?? 0) : totalTasks;

  return (
    <div className="flex gap-4">
      <div className="flex-1">
        {Object.entries(tasks).map(([taskName, statuses]) => (
          <TaskProgress
            key={taskName}
            total={totalsByTask[taskName] ?? 0}
            taskName={taskName}
            isSelected={selectedTask === taskName}
            onTaskSelect={setSelectedTask}
            statuses={statuses}
          />
        ))}
      </div>
      <div className="flex-1">
        <h3 className="font-bold">Tasks statuses</h3>

        <div className="flex flex-col gap-1 mt-2">
          {orderedStatuses.map((status) => {
            const statusTotal = selectedTask ? (tasks[selectedTask]?.[status] ?? 0) : (totalsByStatus[status] ?? 0);

            const percentage = total > 0 ? (statusTotal / total) * 100 : 0;

            return <StatusTasks key={status} statusTotal={statusTotal} percentage={percentage} status={status} />;
          })}
        </div>
      </div>
    </div>
  );
};
