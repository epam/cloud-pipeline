import apiGet from '../base/api-get';

export default function getToolSettings(tool, version = 'latest') {
  return new Promise((resolve, reject) => {
    apiGet(`tool/${tool}/settings`, {version})
      .then(result => {
        const {status, message, payload = []} = result;
        if (status === 'OK') {
          if (payload.length > 0) {
            const {settings = []} = payload[0];
            const [defaultSettings] = settings.filter(s => s.default);
            if (defaultSettings) {
              resolve(defaultSettings.configuration);
            } else {
              resolve({});
            }
          } else {
            resolve({});
          }
        } else {
          reject(new Error(message || `Error fetching tool settings: status ${status}`));
        }
        resolve();
      })
      .catch(reject);
  });
}
