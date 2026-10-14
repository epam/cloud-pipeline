import apiPost from '../base/api-post';

export default function resumeRun(id) {
  return new Promise((resolve, reject) => {
    apiPost(
      `run/${id}/resume`,
      {}
    )
      .then((result) => {
        const {status, message, payload} = result;
        if (status === 'OK') {
          resolve(payload);
        } else {
          reject(new Error(message || `Error resuming run: status ${status}`));
        }
      })
      .catch(reject);
  });
}
