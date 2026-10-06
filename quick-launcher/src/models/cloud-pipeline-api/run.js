import apiGet from '../base/api-get';

export default function getRun(id) {
  return apiGet(`run/${id}`);
}
