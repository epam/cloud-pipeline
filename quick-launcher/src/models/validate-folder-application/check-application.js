import apiCall from '../base/api-call';

export default function checkApplication (endpoint, path, token) {
  return apiCall(
    endpoint && endpoint.endsWith('/') ? endpoint : `${endpoint || ''}/`,
    {
      target_folder: path
    },
    'GET',
    undefined,
    {
      absoluteUrl: true,
      credentials: true,
      headers: {
        bearer: token
      }
    }
  );
}
