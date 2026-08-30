import fetchMountsForPlaceholders from "../parse-limit-mounts-placeholders-config";

export default function buildGatewaySpec (appInfo, settings) {
  if (!appInfo || !settings) {
    return Promise.resolve({});
  }
  const result = {
    instance: appInfo.instance,
    mounts: appInfo.mounts
  };
  const extendPlaceholders = [];
  Object.entries(settings.limitMountsPlaceholders || {})
    .forEach(([placeholder, config]) => {
      if (appInfo[placeholder]) {
        result[placeholder] = appInfo[placeholder];
      } else if (config.default) {
        extendPlaceholders.push({placeholder, config});
      }
    });
  if (extendPlaceholders.length === 0) {
    return Promise.resolve(result);
  }
  return new Promise(resolve => {
    fetchMountsForPlaceholders(extendPlaceholders)
      .then(mountsForPlaceholders => {
        extendPlaceholders.forEach(({placeholder, config}) => {
          if (mountsForPlaceholders[placeholder] && mountsForPlaceholders[placeholder].length > 0) {
            const defaultStorage = mountsForPlaceholders[placeholder]
              .find(storage => `${storage.id}` === `${config.default}`);
            if (defaultStorage) {
              result[placeholder] = defaultStorage.name;
            }
          }
        });
        resolve(result)
      })
      .catch(() => resolve(result));
  });
}
