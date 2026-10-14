import apiCall from './api-call';

export default function apiDelete (uri, body, query = {}) {
  return apiCall(uri, query, 'DELETE', body);
}
