import apiGet from '../base/api-get';

function getAvailableDataStorages() {
  return new Promise((resolve, reject) => {
    apiGet('datastorage/available')
      .then(result => {
        const {status, message, payload: storages = []} = result;
        if (status === 'OK') {
          resolve(storages);
        } else {
          reject(new Error(message || `Error fetching data storages: status ${status}`));
        }
      })
      .catch(reject);
  });
}

const promise = getAvailableDataStorages();

const safePromise = () => new Promise(resolve => {
  promise
    .then(() => resolve())
    .catch(() => resolve());
})

export {safePromise};
export default promise;
