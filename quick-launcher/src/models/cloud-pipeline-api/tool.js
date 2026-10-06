import apiGet from '../base/api-get';

export default function getTool (toolId) {
  return new Promise((resolve, reject) => {
    apiGet('tool/load', {image: toolId})
      .then(result => {
        const {status, message, payload = []} = result;
        if (status === 'OK') {
          resolve(payload);
        } else {
          reject(new Error(message || `Error fetching tool: status ${status}`));
        }
        resolve();
      })
      .catch(reject);
  });
}
