import apiDelete from '../base/api-delete';
import asArray from "../utilities/as-array";

export default function removeStorageFolder(dataStorageId, path) {
  const payload = asArray(path).map((aPath) => ({path: aPath, type: 'Folder'}));
  return new Promise((resolve, reject) => {
    apiDelete(
      `datastorage/${dataStorageId}/list?totally=false`,
      payload
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
