import type { EngineTasks } from '@cloud-pipeline/core';

export const calculateStatusTotals = (data: EngineTasks) => {
  console.log(data);
  const totalsByStatus: Record<string, number> = {};
  const totalsByTask: Record<string, number> = {};
  let totalTasks = 0;

  const entries = Object.entries(data);

  for (let i = 0; i < entries.length; i++) {
    const [taskName, taskStatuses] = entries[i];

    const statusesEntries = Object.entries(taskStatuses);

    for (let j = 0; j < statusesEntries.length; j++) {
      const [status, count] = statusesEntries[j];
      totalTasks += count;

      if (!totalsByStatus[status]) {
        totalsByStatus[status] = 0;
      }

      totalsByStatus[status] += count;

      if (!totalsByTask[taskName]) {
        totalsByTask[taskName] = 0;
      }

      totalsByTask[taskName] += count;
    }
  }

  return {
    totalTasks,
    totalsByStatus,
    totalsByTask,
  };
};
