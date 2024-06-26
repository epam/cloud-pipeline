import apiGet from '../base/api-get';

export default function getDataStorage(id) {
  return new Promise((resolve, reject) => {
    apiGet(`datastorage/${id}/load`)
      .then(result => {
        const {status, message, payload: storage} = result;
        if (status === 'OK') {
          resolve(storage);
        } else {
          reject(new Error(message || `Error fetching storage: status ${status}`));
        }
      })
      .catch(reject);
  });
}
