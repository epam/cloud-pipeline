import apiGet from '../base/api-get';

export default function getRunTasks(id) {
  return apiGet(`run/${id}/tasks`);
}
