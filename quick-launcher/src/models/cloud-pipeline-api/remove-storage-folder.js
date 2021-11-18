import apiDelete from '../base/api-delete';

export default function removeStorageFolder(dataStorageId, path) {
  return new Promise((resolve, reject) => {
    apiDelete(
      `datastorage/${dataStorageId}/list?totally=false`,
      [{path, type: 'Folder'}]
    )
      .then((result) => {
        const {status, message, payload} = result;
        if (status === 'OK') {
          resolve(payload);
        } else {
          reject(new Error(message || `Error removing folder: status ${status}`));
        }
      })
      .catch(reject);
  });
}
