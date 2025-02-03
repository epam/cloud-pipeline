const DTS_ENVIRONMENT = 'DTS';
const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';

export function isDtsEnvironment (run) {
  return run && run.executionPreferences &&
    run.executionPreferences.environment === DTS_ENVIRONMENT;
}

export function isFireCloudEnvironment (run) {
  return run && run.executionPreferences &&
    run.executionPreferences.environment === FIRE_CLOUD_ENVIRONMENT;
}
