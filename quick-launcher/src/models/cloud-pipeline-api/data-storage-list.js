import apiGet from '../base/api-get';

export default function getDataStorageItems (
  storage,
  path,
  marker = undefined,
  pageSize = 100
) {
  const query = [
    'showVersion=false',
    `pageSize=${pageSize}`,
    path ? `path=${encodeURIComponent(path)}` : undefined,
    marker ? `marker=${marker}` : undefined
  ].filter(Boolean).join('&');
  return new Promise((resolve, reject) => {
    apiGet(`datastorage/${storage}/list/page?${query}`)
      .then(payload => {
        const {
          status,
          message,
          payload: contents = {}
        } = payload || {};
        if (/^ok$/i.test(status)) {
          const {
            results = [],
            nextPageMarker
          } = contents;
          resolve({results, nextPageMarker});
        } else {
          throw new Error(`Error fetching storage #${storage} content at path ${path}: ${message || 'unknown error'}`);
        }
      })
      .catch(reject);
  })
}
