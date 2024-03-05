import apiGet from '../base/api-get';

export default function getRoles() {
  return new Promise((resolve, reject) => {
    apiGet('role/loadAll')
      .then(result => {
        const {status, message, payload: roles = []} = result;
        if (status === 'OK') {
          resolve(roles);
        } else {
          reject(new Error(message || `Error fetching roles: status ${status}`));
        }
      })
      .catch(reject);
  });
}
