import getDataStorage from '../cloud-pipeline-api/data-storage-info';
import PreRunCheckError from './pre-run-check-error';

const MESSAGE = `Your home storage is in a read only state.`

const DISCLAIMER = `### Your home storage is in a **read only** state.

This may cause application to fail during the initialization. Please consider cleaning the storage.`

class CheckUserStorageStateError extends PreRunCheckError {
  constructor(disclaimer) {
    super(
      MESSAGE,
      disclaimer || DISCLAIMER
    );
  }
}

export default function checkUserStorage(settings, user) {
  if (!(settings?.checkDefaultUserStorageStatus)) {
    console.log('Skipping user\'s default storage check (`checkDefaultUserStorageStatus`=false)');
    return Promise.resolve();
  }
  if (!user) {
    console.log('Skipping user\'s default storage check: unknown user')
    return Promise.resolve();
  }
  if (settings?.isAnonymous) {
    console.log('Skipping user\'s default storage check: anonymous user');
    return Promise.resolve();
  }
  if (!user.defaultStorageId) {
    console.log('Skipping user\'s default storage check: default storage is not set for the user', user.userName);
    return Promise.resolve();
  }
  console.log(`Checking ${user.userName} default storage (#${user.defaultStorageId}) status...`);
  return new Promise((resolve, reject) => {
    getDataStorage(user.defaultStorageId)
      .then((storage) => {
        const {
          mountStatus
        } = storage || {};
        console.log(
          `Checking ${user.userName} default storage (#${user.defaultStorageId}) status: ${mountStatus}`
        );
        if (/^(MOUNT_DISABLED|READ_ONLY)$/i.test(mountStatus)) {
          reject(new CheckUserStorageStateError(settings?.defaultUserStorageReadOnlyWarning));
        } else {
          resolve(true);
        }
      })
      .catch(reject);
  });
}
