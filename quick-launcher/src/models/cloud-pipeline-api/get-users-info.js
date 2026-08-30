import apiGet from '../base/api-get';

export default function getUsersInfo() {
  return new Promise((resolve, reject) => {
    apiGet('users/info')
      .then(result => {
        const {status, message, payload: users = []} = result;
        if (status === 'OK') {
          resolve(users);
        } else {
          reject(new Error(message || `Error fetching users: status ${status}`));
        }
      })
      .catch(reject);
  });
}
