import PathComponent from '../utilities/path-component';
import removeExtraSlash from '../utilities/remove-slashes';
import getDataStorageContents from '../cloud-pipeline-api/data-storage-contents';
import parseStoragePlaceholder from '../parse-storage-placeholder';
import readApplicationInfo from './read-application-info';
import { getApplicationTypeSettings } from "../folder-application-types";

export default function fetchFolderApplication (path, globalSettings, appType) {
  console.log('fetch folder application of type', appType || '<default>', 'by path', path);
  const settings = getApplicationTypeSettings(globalSettings, appType);
  if (!settings || !settings.appConfigPath || !settings.appConfigStorage || !path) {
    return Promise.resolve();
  }
  const storage = Number(parseStoragePlaceholder(settings.appConfigStorage));
  if (Number.isNaN(storage)) {
    return Promise.resolve();
  }
  const appConfigPath = removeExtraSlash(settings.appConfigPath);
  const gatewaySpecFileName = appConfigPath.split('/').pop();
  const pathComponent = new PathComponent({
    path: appConfigPath,
    hasPlaceholders: true,
    gatewaySpecFile: true
  });
  let gatewaySpecFilePath = removeExtraSlash(path);
  if (!pathComponent.parsePathComponent(gatewaySpecFilePath)) {
    gatewaySpecFilePath = `${gatewaySpecFilePath}/${gatewaySpecFileName}`;
  }
  const pathInfo = pathComponent.parsePathComponent(gatewaySpecFilePath);
  if (!pathInfo) {
    return Promise.resolve();
  }
  const userProperty = settings.folderApplicationUserPlaceholder || 'user';
  const userName = pathInfo[userProperty];
  if (!userName) {
    return Promise.resolve();
  }
  delete pathInfo[settings.folderApplicationUserPlaceholder];
  const user = {userName};
  const folder = gatewaySpecFilePath.split('/').slice(0, -1).join('/');
  return new Promise((resolve) => {
    getDataStorageContents(storage, folder)
      .then(contents => {
        const icon = contents.find(o => /^gateway.(png|jpg|tiff|jpeg|svg)$/i.test(o.name));
        const app = {
          icon,
          info: pathInfo,
          path: gatewaySpecFilePath,
          storage
        }
        return readApplicationInfo(app, user, settings, appType);
      })
      .then(resolve);
  });
}
