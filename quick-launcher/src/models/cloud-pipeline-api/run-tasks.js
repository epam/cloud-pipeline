import apiGet from '../base/api-get';

export async function getRunTaskLogs(id, taskName) {
  const {payload, message, status} = await apiGet(`run/${id}/task`, { taskName })
  if (/^ok$/i.test(status)) {
    return payload || [];
  }
  throw new Error(message || `Error fetching task "${taskName}" logs`);
}

export default function getRunTasks(id) {
  return apiGet(`run/${id}/tasks`);
}
