import getDataStorageItems from './data-storage-list';

export default function getDataStorageContents (
  storage,
  path
) {
  const processMarker = (marker, initial = true) => {
    if (!marker && !initial) {
      return Promise.resolve([]);
    }
    return new Promise((resolve) => {
      getDataStorageItems(storage, path, marker)
        .then(result => {
          const {results = [], nextPageMarker} = result || {};
          return Promise.all([
            Promise.resolve(results),
            processMarker(nextPageMarker, false)
          ]);
        })
        .then(payloads => resolve(payloads.reduce((r, c) => ([...r, ...c]), [])))
        .catch(e => {
          console.warn(e.message);
          resolve([]);
        });
    });
  }
  return processMarker();
}
