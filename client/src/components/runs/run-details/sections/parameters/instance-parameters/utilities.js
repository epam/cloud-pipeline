const DTS_ENVIRONMENT = 'DTS';

export function isDtsEnvironment (run) {
  return run && run.executionPreferences &&
    run.executionPreferences.environment === DTS_ENVIRONMENT;
}
