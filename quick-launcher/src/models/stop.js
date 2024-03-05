import launches from './cloud-pipeline-api/active-launches';
import stopRun from './cloud-pipeline-api/stop-run';

export default function stopApplication(applicationId, user) {
  if (!CPAPI) {
    return Promise.resolve();
  } else {
    // Cloud Pipeline ID:
    const runIdKey = `${applicationId}-${user || ''}`;
    if (launches.has(runIdKey)) {
      if (TOOLS) {
        launches.delete(runIdKey);
        return Promise.resolve();
      } else {
        const runId = launches.get(runIdKey);
        launches.delete(runIdKey);
        return new Promise((resolve, reject) => {
          stopRun(runId)
            .then(result => {
              const {status: requestStatus, message, payload: run} = result;
              if (requestStatus === 'OK') {
                launches.delete(runIdKey);
                resolve();
              } else {
                reject(new Error(message || `Error stopping application: status ${requestStatus}`));
              }
            })
            .catch(reject);
        });
      }
    }
  }
  return Promise.resolve();
}
