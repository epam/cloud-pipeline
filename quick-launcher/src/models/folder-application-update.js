import fetchFolderApplications from './fetch-folder-applications';
import removeDataStorageItem from './cloud-pipeline-api/data-storage-item-remove';
import combineUrl from './base/combine-url';
import updateDataStorageItem from './cloud-pipeline-api/data-storage-item-update';
import {safelyUpdateApplication} from './folder-applications-list';

function updateIcon (settings, application, destinationFolder, file) {
  if (!file || !application || !destinationFolder) {
    return Promise.resolve();
  }
  const destination = destinationFolder.endsWith('/') ? destinationFolder.slice(0, -1) : destinationFolder;
  const newFilePath = `${destination}/gateway.${file.name.split('.').pop()}`;
  const uploadFile = (url, file) => new Promise((resolve) => {
    const formData = new FormData();
    formData.append('file', file, `gateway.${file.name.split('.').pop()}`);
    const request = new XMLHttpRequest();
    request.withCredentials = true;
    request.onreadystatechange = function () {
      if (request.readyState !== 4) return;
      resolve();
    };
    request.open('POST', url);
    request.send(formData);
  });
  const safelyRemoveCurrentIcon = (path) => new Promise((resolve) => {
    removeDataStorageItem(
      application.storage,
      path
    )
      .then(resolve)
      .catch(() => resolve());
  });
  return new Promise((resolve) => {
    let removeCurrentIconPromise;
    if (application.iconFile && application.iconFile.path) {
      const currentIconFilePath = `${destination}/${application.iconFile.path.split('/').pop()}`
      removeCurrentIconPromise = safelyRemoveCurrentIcon(currentIconFilePath);
    } else {
      removeCurrentIconPromise = Promise.resolve();
    }
    removeCurrentIconPromise
      .then(() => {
        const url = combineUrl(
          settings.api,
          `datastorage/${application.storage}/list/upload?path=${destination}`
        )
        return uploadFile(url, file);
      })
      .then(() => {
        application.iconFile = {
          path: newFilePath
        };
      })
      .then(resolve)
      .catch((e) => {
        console.error(`Error updating icon: ${e.message}`);
        resolve();
      });
  });
}

export default function folderApplicationUpdate (
  settings,
  application,
  spec,
  icon,
  reloadApplications = true,
  callback
) {
  if (!settings || !settings.serviceUser) {
    return Promise.reject(new Error('Service user is not defined'));
  }
  if (!application || !application.info) {
    return Promise.reject(new Error('Application is not defined'));
  }
  if (!application || !application.storage) {
    return Promise.reject(new Error('Application storage is not defined'));
  }
  const {
    path: destinationGatewaySpec
  } = application;
  let destination = destinationGatewaySpec.split('/').slice(0, -1).join('/');
  if (!destination.endsWith('/')) {
    destination = destination.concat('/');
  }
  return new Promise((resolve, reject) => {
    Promise.resolve()
      .then(() => {
        if (icon) {
          callback && callback('Updating icon', 0);
          return updateIcon(settings, application, destination, icon);
        }
        return Promise.resolve();
      })
      .then(() => {
        callback && callback('Updating icon', 0.33);
        if (spec) {
          callback && callback('Updating application info', 0.33);
          return updateDataStorageItem(
            application.storage,
            destinationGatewaySpec,
            JSON.stringify(spec)
          );
        }
        return Promise.resolve();
      })
      .then(() => {
        callback && callback('Updating metadata file...', 0.66);
        return safelyUpdateApplication(settings, settings.serviceUser, application);
      })
      .then(() => {
        callback && callback(undefined, 0.8);
        if (!reloadApplications) {
          return Promise.resolve();
        }
        const options = settings.parseUrl(window.location.href);
        return fetchFolderApplications({options, force: true});
      })
      .then(() => {
        callback && callback(undefined, 1);
        resolve();
      })
      .catch(e => {
        console.error(e.message);
        reject(e);
      });
  });
}
