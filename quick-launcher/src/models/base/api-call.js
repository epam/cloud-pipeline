import combineUrl from './combine-url';
import getSettings from './settings';
import getFetchOptions from './get-fetch-options';

function removeTrailingSlash (url) {
  if (!url || !url.endsWith('/')) {
    return url;
  }
  return url.substring(0, url.length - 1);
}

export {combineUrl};
export default function apiCall (uri, query = {}, method = 'GET', body = undefined, options = {}) {
  return new Promise((resolve, reject) => {
    getSettings()
      .then(settings => {
        const {
          absoluteUrl = false,
          headers = {},
          isBlob = false,
          credentials = true
        } = options;
        try {
          const parsedQuery = Object.entries(typeof query === 'object' ? query : {})
            .filter(([, value]) => !!value)
            .map(([key, value]) => `${key}=${encodeURIComponent(value)}`);
          const queryString = parsedQuery.length > 0 ? `?${parsedQuery.join('&')}` : '';
          fetch(
            absoluteUrl
              ? `${uri}${queryString}`
              : combineUrl(settings.api, `/${uri}${queryString}`),
            {
              ...getFetchOptions(settings, {credentials, headers}),
              body: body ? JSON.stringify(body) : undefined,
              method: method || (body ? 'POST' : 'GET')
            }
          )
            .then(response => {
              const codeFamily = Math.ceil(response.status / 100);
              if (codeFamily === 4 || codeFamily === 5) {
                if (response.status === 401 && settings.redirectOnAPIUnauthenticated) {
                  const authEndpoint = combineUrl(
                    settings.api,
                    `/route?url=${removeTrailingSlash(document.location.href)}/&type=COOKIE`
                  );
                  console.log(`"${uri}" got 401 error. Redirecting to ${authEndpoint}`);
                  window.location = authEndpoint;
                }
                reject(new Error(`Response: ${response.status} ${response.statusText}`));
              } else {
                if (isBlob) {
                  response
                    .blob()
                    .then(resolve)
                    .catch(reject);
                } else {
                  response
                    .json()
                    .then(resolve)
                    .catch(reject);
                }
              }
            })
            .catch(reject);
        } catch (e) {
          reject(e);
        }
      })
      .catch(reject);
  });
}
