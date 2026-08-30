import searchMetadata from "./cloud-pipeline-api/metadata-search";
import getAvailableDataStorages from "./cloud-pipeline-api/data-storage-available";

function fetchMountsByTag(tag, value, storages) {
  return new Promise((resolve) => {
    searchMetadata('DATA_STORAGE', tag, value)
      .then((result) => {
        const {payload: mounts = []} = result || {};
        resolve(
          mounts
            .map(({entityId}) => storages.find(s => +(s.id) === +entityId))
            .filter(Boolean)
        );
      })
      .catch(() => {
        resolve([])
      });
  });
}

let availableStoragesPromise;

export default function fetchMountsForPlaceholders (placeholders) {
  const wrapper = (placeholder, storages) => new Promise(resolve => {
    fetchMountsByTag(placeholder.config.tagName, placeholder.config.tagValue, storages)
      .then(mounts => resolve(mounts.length ? {[placeholder.placeholder]: mounts} : []))
  });
  return new Promise((resolve) => {
    getAvailableDataStorages
      .then(storages => {
        Promise.all(placeholders.map(placeholder => wrapper(placeholder, storages)))
          .then(results => {
            resolve(results.reduce((res, cur) => ({...res, ...cur}), {}));
          });
      })
      .catch(() => resolve({}));
  });
}
