import apiPost from '../base/api-post';

export default function getUserRuns(user) {
  const payload = {
    'page': 1,
    'pageSize': 1000,
    'statuses': ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'],
    'owners': [user],
    'userModified': false
  }
  return new Promise((resolve, reject) => {
    apiPost('run/filter', payload)
      .then((result) => {
        const {status, message, payload} = result;
        if (status === 'OK') {
          const {elements = []} = payload;
          resolve(elements);
        } else {
          reject(new Error(message || `Error fetching runs: status ${status}`));
        }
      })
      .catch(reject);
  });
}
