import getDataStorageItemContent from './cloud-pipeline-api/data-storage-item-content';

export default function fetchFolderApplicationInfo (dataStorageId, path) {
  return new Promise((resolve) => {
    getDataStorageItemContent(dataStorageId, path)
      .then(content => {
        try {
          resolve(JSON.parse(content));
        } catch (_) {
          resolve({});
        }
      })
      .catch(() => resolve(undefined));
  });
}
