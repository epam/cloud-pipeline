export function getFolderApplicationTypes (settings) {
  if (
    !settings ||
    !settings.applicationTypes
  ) {
    return [undefined];// default behavior
  }
  const types = Object.keys(settings?.applicationTypes || {})
    .filter(appType =>
      !!settings?.applicationTypes[appType]?.appConfigPath &&
      !!settings?.applicationTypes[appType]?.appConfigStorage
    );
  if (types.length === 0) {
    return [undefined];// default behavior
  }
  return types;
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
