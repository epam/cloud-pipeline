import apiCall, {APICallError} from '../base/api-call';

function checkApplication (endpoint, path, token) {
  return apiCall(
    endpoint && endpoint.endsWith('/') ? endpoint : `${endpoint || ''}/`,
    {
      target_folder: path
    },
    'GET',
    undefined,
    {
      absoluteUrl: true,
      credentials: true,
      headers: {
        bearer: token
      }
    }
  );
}

function checkApplicationContinuous (endpoint, path, token, settings) {
  const maxAttempts = settings?.folderApplicationValidation?.endpointPollingMaxRequests || 5;
  const interval = settings?.folderApplicationValidation?.endpointPollingIntervalMS || 3000;
  const errorCodes = settings?.folderApplicationValidation?.endpointPollingErrorCodes || [404, 502];
  return new Promise((resolve, reject) => {
    let attempts = 0;
    const wrap = () => {
      attempts += 1;
      if (attempts > 1) {
        console.log('Check folder application attempt', attempts, 'of', maxAttempts);
      }
      checkApplication(endpoint, path, token)
        .then(resolve)
        .catch(error => {
          if (
            error instanceof APICallError &&
            attempts < maxAttempts &&
            errorCodes.includes(error.status)
          ) {
            setTimeout(() => wrap(), interval);
          } else {
            reject(error);
          }
        });
    };
    wrap();
  });
}

export default checkApplicationContinuous;
