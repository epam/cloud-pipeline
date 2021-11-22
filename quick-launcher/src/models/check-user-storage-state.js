import getDataStorage from './cloud-pipeline-api/data-storage-info';
import confirmation from '../components/shared/confirmation';

class CheckUserStorageStateError extends Error {
  constructor() {
    super('Your home storage is in a read only state. This may cause application to fail during the initialization. Please consider cleaning the storage');
    this.title = 'Your home storage is in a read only state.'
    this.body = 'This may cause application to fail during the initialization. Please consider cleaning the storage.';
  }
}

export {CheckUserStorageStateError};

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
          throw new CheckUserStorageStateError();
        } else {
          throw new CheckUserStorageStateError();
          // resolve();
        }
      })
      .catch(e => {
        console.warn(`Error checking default storage state: ${e.message}`);
        if (e instanceof CheckUserStorageStateError) {
          return confirmation({
            title: e.title,
            message: e.body
          });
        } else {
          return Promise.reject(e);
        }
      })
      .then(result => {
        if (result === undefined || result) {
          console.log('Checking default storage: passed');
          resolve();
        } else {
          reject(new CheckUserStorageStateError());
        }
      })
      .catch(reject);
  });
}
