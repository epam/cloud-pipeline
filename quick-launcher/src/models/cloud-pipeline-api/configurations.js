import apiGet from '../base/api-get';

export default function getApplications() {
  return apiGet('configuration/loadAll');
}
