import apiGet from '../base/api-get';

function removeLeadingSlash(string) {
  if (!string || !string.startsWith('/')) {
    return string;
  }
  return string.substr(1);
}

export default function getDataStorageItemContent(dataStorageId, path) {
  return new Promise((resolve, reject) => {
    apiGet(
      `datastorage/${dataStorageId}/content`,
      {path: path ? removeLeadingSlash(path) : undefined}
      )
      .then(result => {
        const {status, message, payload = []} = result;
        if (status === 'OK') {
          if (payload.content) {
            try {
              resolve(atob(payload.content));
            } catch (e) {
              reject(new Error(message || `Error decoding "${path}" (storage #${dataStorageId}) content: ${e.message}`));
            }
          } else {
            resolve(undefined);
          }
        } else {
          reject(new Error(message || `Error fetching "${path}" (storage #${dataStorageId}) content: status ${status}`));
        }
        resolve();
      })
      .catch(reject);
  });
}
