import getDataStorage from '../cloud-pipeline-api/data-storage-info';
import removeExtraSlash from '../utilities/remove-slashes';
import processString from '../process-string';

export default function buildApplicationPath (applicationStorage, path, settings) {
  if (
    !applicationStorage ||
    !path ||
    !settings ||
    !settings.folderApplicationValidation ||
    !settings.folderApplicationValidation.applicationPath
  ) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    getDataStorage(applicationStorage)
      .then(storageInfo => {
        if (!storageInfo) {
          throw new Error(`storage #${applicationStorage} not found`);
        }
        const application_storage_path = removeExtraSlash(
          (storageInfo.path || '').replace(/:\//g, '/')
        );
        const placeholders = {
          application_storage_path,
          application_storage_id: applicationStorage,
          application_path: removeExtraSlash(path)
        };
        resolve(
          processString(
            settings.folderApplicationValidation.applicationPath,
            placeholders
          )
        );
      })
      .catch(e => {
        console.warn(`Error building application absolute path: ${e.message}`);
        resolve();
      });
  });
}
