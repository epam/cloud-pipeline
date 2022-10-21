import removeDataStorageItem from './cloud-pipeline-api/data-storage-item-remove';
import fetchFolderApplications from './fetch-folder-applications';
import {safelyRemoveApplication} from './folder-applications-list';

export default function folderApplicationRemove (application, settings, callback) {
  if (!application || !application.info) {
    return Promise.reject(new Error('Application is not defined'));
  }
  if (!application || !application.storage) {
    return Promise.reject(new Error('Application storage is not defined'));
  }
  const {
    published,
    info = {}
  } = application;
  if (!published) {
    return Promise.reject(new Error('Application is not published'));
  }
  let source = info.path;
  if (!source) {
    return Promise.reject(new Error('Application source folder is not defined'));
  }
  if (!source.endsWith('/')) {
    source = source.concat('/');
  }
  console.log('Removing folder application', application.name, 'from', source);
  return new Promise((resolve, reject) => {
    callback && callback('Removing application files...', 0.1);
    removeDataStorageItem(application.storage, source)
      .then(() => {
        callback && callback('Updating metadata file...', 0.5);
        return safelyRemoveApplication(settings, settings.serviceUser, application)
      })
      .then(() => {
        const options = settings.parseUrl(window.location.href)
        callback && callback('Finishing', 0.95);
        return fetchFolderApplications({options, force: true});
      })
      .then(resolve)
      .catch(reject);
  });
}
