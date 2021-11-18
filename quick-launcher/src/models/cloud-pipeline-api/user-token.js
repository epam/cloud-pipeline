import apiGet from '../base/api-get';

export default function getUserToken (expiration = 60) {
  return new Promise((resolve, reject) => {
    apiGet('user/token')
      .then(result => {
        const {status, message, payload} = result;
        if (status === 'OK') {
          const {token} = payload;
          resolve(token);
        } else {
          throw new Error(message || `Error fetching user token: status ${status}`);
        }
      })
      .catch(reject);
  });
}
