import PreRunCheckError from './pre-run-check-error';
import checkUserStorage from './check-user-storage-state';
import confirmation from '../../components/shared/confirmation';
import checkSensitiveStorages from './check-sensitive-storages';
import checkDisablePackages from "./check-disable-packages";

function getSingleCheckError (check) {
  return new Promise((resolve, reject) => {
    check
      .then(() => resolve(undefined))
      .catch(error => error instanceof PreRunCheckError ? resolve(error) : reject(error));
  });
}

function showConfirmation (errors = []) {
  if (!errors || errors.length === 0) {
    return Promise.resolve(true);
  }
  return new Promise((resolve, reject) => {
    confirmation({
      md: errors.map(o => o.disclaimer).join('\n_____\n')
    })
      .then(confirmed => {
        if (confirmed) {
          resolve(true);
        } else {
          reject(new Error(`Cancelled: ${errors.map(o => o.message).join('; ')}`));
        }
      });
  });
}

export default function performPreRunChecks (settings, options = {}) {
  const {
    user,
    launchOptions,
    application,
  } = options;
  return new Promise((resolve, reject) => {
    Promise.all([
      getSingleCheckError(checkUserStorage(settings, user)),
      getSingleCheckError(checkSensitiveStorages(settings, launchOptions)),
      getSingleCheckError(checkDisablePackages(settings, application)),
    ])
      .then(results => {
        const errors = results.filter(Boolean);
        return showConfirmation(errors);
      })
      .then(resolve)
      .catch(reject);
  });
}
