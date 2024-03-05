import getDataStorageItemContent from './cloud-pipeline-api/data-storage-item-content';

export default function fetchFolderApplicationInfo (dataStorageId, path) {
  return new Promise((resolve) => {
    getDataStorageItemContent(dataStorageId, path)
      .then(content => {
        try {
          resolve(JSON.parse(content));
        } catch (_) {
          const message = _.message || '';
          resolve({
            __gatewaySpecError__: `gateway.spec: ${message.slice(0, 1).toLowerCase()}${message.slice(1)}`
          });
        }
      })
      .catch(() => resolve(undefined));
  });
}
