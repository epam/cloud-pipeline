import apiCall from './api-call';

export default function apiGet (uri, query = {}) {
  return apiCall(uri, query);
}
