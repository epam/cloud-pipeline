import parseStoragePlaceholder from './parse-storage-placeholder';
import processString from './process-string';
import getDataStorageItemContent from './cloud-pipeline-api/data-storage-item-content';
import PathComponent from './utilities/path-component';
import removeExtraSlash from './utilities/remove-slashes';
import updateDataStorageItem from "./cloud-pipeline-api/data-storage-item-update";
import { getApplicationTypeSettings } from "./folder-application-types";

function processApplication (application, configuration, settings) {
  const {
    icon,
    path,
    storage,
    ...rest
  } = application;
  const processedPath = removeExtraSlash(path);
  const info = configuration.parsePathComponent(processedPath);
  delete info[settings.folderApplicationUserPlaceholder || 'user'];
  return {
    ...rest,
    icon: icon ? {path: icon} : undefined,
    storage,
    path,
    info
  }
}

function getFolderApplicationsFileConfig (settings, userName) {
  if (
    !settings ||
    !settings.folderApplicationsListStorage ||
    !settings.folderApplicationsListFile
  ) {
    return Promise.reject(new Error('[WARNING] Folder applications spec file is not specified'));
  }
  if (!settings.appConfigPath) {
    return Promise.reject(new Error('<appConfigPath> is not specified'));
  }
  const dataStorageId = parseStoragePlaceholder(settings.folderApplicationsListStorage, {userName});
  if (Number.isNaN(Number(dataStorageId))) {
    return Promise.reject(new Error(`Unknown folder applications spec file storage: ${dataStorageId}`));
  }
  const path = processString(
    settings.folderApplicationsListFile,
    {
      [settings.folderApplicationUserPlaceholder || 'user']: userName
    }
  );
  return Promise.resolve({path, storage: dataStorageId});
}

export function getApplications (settings, userName, appType) {
  const appTypeSettings = getApplicationTypeSettings(settings, appType);
  if (!appTypeSettings) {
    return Promise.resolve([]);
  }
  return new Promise((resolve, reject) => {
    getFolderApplicationsFileConfig(appTypeSettings, userName)
      .then(({path, storage: dataStorageId}) => {
        getDataStorageItemContent(dataStorageId, path)
          .then((content) => {
            try {
              const applications = JSON.parse(content);
              if (!Array.isArray(applications)) {
                throw new Error('wrong file format; expected: array');
              }
              const pathComponent = new PathComponent({
                path: removeExtraSlash(appTypeSettings.appConfigPath),
                hasPlaceholders: true,
                gatewaySpecFile: true
              });
              resolve(applications.map(app => processApplication(app, pathComponent, appTypeSettings)));
            } catch (e) {
              reject(new Error(`Error parsing folder applications spec file: ${e.message}`));
            }
          })
          .catch(e => {
            reject(new Error(`Error reading folder applications spec file: ${e.message}`));
          });
      })
      .catch(reject);
  });
}

function updateApplication (settings, userName, application) {
  return new Promise((resolve, reject) => {
    getFolderApplicationsFileConfig(settings, userName)
      .then(({path, storage: dataStorageId}) => {
        getDataStorageItemContent(dataStorageId, path)
          .then((content) => {
            try {
              const applications = JSON.parse(content);
              if (!Array.isArray(applications)) {
                throw new Error('wrong file format; expected: array');
              }
              return Promise.resolve(applications);
            } catch (e) {
              return Promise.resolve([]);
            }
          })
          .catch(() => resolve([]))
          .then((applications = []) => {
            const index = applications
              .findIndex(app => removeExtraSlash(app.path) === removeExtraSlash(application.path) &&
                +(app.storage) === +(application.storage)
              );
            const newAppInfo = {
              path: application.path,
              storage: application.storage,
              icon: application?.iconFile?.path
            };
            if (index >= 0) {
              applications.splice(index, 1, newAppInfo);
            } else {
              applications.push(newAppInfo);
            }
            return updateDataStorageItem(
              dataStorageId,
              path,
              JSON.stringify(applications, undefined, ' ')
            );
          })
          .then(resolve)
          .catch(reject);
      })
      .catch(reject);
  });
}

function removeApplication (settings, userName, application) {
  return new Promise((resolve, reject) => {
    getFolderApplicationsFileConfig(settings, userName)
      .then(({path, storage: dataStorageId}) => {
        getDataStorageItemContent(dataStorageId, path)
          .then((content) => {
            try {
              const applications = JSON.parse(content);
              if (!Array.isArray(applications)) {
                throw new Error('wrong file format; expected: array');
              }
              return Promise.resolve(applications);
            } catch (e) {
              return Promise.resolve([]);
            }
          })
          .catch(() => {})
          .then((applications = []) => {
            const index = applications
              .findIndex(app => removeExtraSlash(app.path) === removeExtraSlash(application.path) &&
                +(app.storage) === +(application.storage)
              );
            if (index >= 0) {
              applications.splice(index, 1);
            }
            return updateDataStorageItem(
              dataStorageId,
              path,
              JSON.stringify(applications, undefined, ' ')
            );
          })
          .then(resolve)
          .catch(reject);
      })
      .catch(reject);
  });
}

export function safelyUpdateApplication(settings, userName, application) {
  return new Promise((resolve) => {
    updateApplication(settings, userName, application)
      .catch(() => {})
      .then(resolve);
  });
}

export function safelyRemoveApplication(settings, userName, application) {
  return new Promise((resolve) => {
    removeApplication(settings, userName, application)
      .catch(() => {})
      .then(resolve);
  });
}
