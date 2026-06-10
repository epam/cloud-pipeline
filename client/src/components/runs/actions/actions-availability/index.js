import {runActions} from './actions';
import preferences from '../../../../models/preferences/PreferencesLoad';

export {runActions};

export function checkRunActionAvailable(run, runAction) {
  if (!run || !runAction) {
    return false;
  }
  preferences.fetchIfNeededOrWait();
  const {uiRunActions = {}} = preferences;
  const criteria = uiRunActions[runAction];
  if (criteria && typeof criteria === 'function') {
    return Boolean(criteria(run));
  }
  return true;
}

export async function checkRunActionAvailableAsync(run, runAction) {
  if (!run || !runAction) {
    return false;
  }
  await preferences.fetchIfNeededOrWait();
  return checkRunActionAvailable(run, runAction);
}
