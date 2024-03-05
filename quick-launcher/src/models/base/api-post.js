import apiCall from './api-call';

export default function apiPost (uri, body, query = {}) {
  return apiCall(uri, query, 'POST', body);
}
