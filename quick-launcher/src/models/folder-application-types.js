export function getFolderApplicationTypes (settings) {
  if (!settings) {
    return [undefined];
  }
  return [
    undefined,// default or "no-type"
    ...Object.keys(settings?.applicationTypes || {})
      .filter(appType =>
        !!settings?.applicationTypes[appType]?.appConfigPath &&
        !!settings?.applicationTypes[appType]?.appConfigStorage
      )
  ];
}

export function getApplicationTypeSettings (settings, appType = undefined) {
  if (!appType) {
    return settings;
  }
  const appTypeSettings =  ((settings || {}).applicationTypes || {})[appType];
  if (appTypeSettings) {
    return {
      ...settings,
      ...appTypeSettings
    };
  }
  return undefined;
}
