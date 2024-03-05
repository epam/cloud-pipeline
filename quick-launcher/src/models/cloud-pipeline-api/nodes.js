import apiGet from '../base/api-get';

export default function getNodes() {
  return new Promise((resolve, reject) => {
    apiGet('cluster/node/loadAll')
      .then(response => {
        const {status, message, payload: nodes = []} = response;
        if (status === 'OK') {
          resolve(nodes);
        } else {
          reject(new Error(message || `Error fetching nodes: status ${status}`));
        }
      })
      .catch(reject);
  });
}
