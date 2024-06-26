import { token } from './bearer-token';

const AUTH_HEADERS = Object.assign(
  {},
  token
    ? {'Authorization': `Bearer ${token}`}
    : {}
);

export default function getFetchOptions(settings, options = {}) {
  const {
    headers = {},
    credentials: includeCredentials = true
  } = options;
  const extraHeaders = settings.useBearerAuth ? AUTH_HEADERS : {};
  const credentials = CPAPI && includeCredentials
    ? 'include'
    : undefined;
  if (settings.isAnonymous && settings.anonymousAccess?.token) {
    extraHeaders['Authorization'] = `Bearer ${settings.anonymousAccess?.token}`;
  }
  return {
    mode: CPAPI ? 'cors' : undefined,
    credentials: settings.isAnonymous && !settings.anonymousAccess?.includeCredentials ? 'omit' : credentials,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
      ...extraHeaders
    }
  };
}
