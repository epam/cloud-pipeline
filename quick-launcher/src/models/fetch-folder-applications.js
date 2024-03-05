import parseStoragePlaceholder from './parse-storage-placeholder';
import processString from './process-string';
import getDataStorageContents from './cloud-pipeline-api/data-storage-contents';
import {getApplications} from './folder-applications-list';
import {ESCAPE_CHARACTERS, escapeRegExp} from './utilities/escape-reg-exp';
import PathComponent, {pathComponentHasPlaceholder} from './utilities/path-component';
import removeExtraSlash from './utilities/remove-slashes';
import readApplicationInfo from './folder-applications/read-application-info';
import {getApplicationTypeSettings, getFolderApplicationTypes} from './folder-application-types';
import fetchSettings from "./base/settings";

const applicationsCache = new Map();

function processIgnoredPaths (settings) {
  const {folderApplicationIgnoredPaths = []} = settings || {};
  const processIgnoredPath = path => {
    const absolute = path.startsWith('/');
    if (absolute) {
      path = path.slice(1);
    }
    if (path.endsWith('/')) {
      path = path.slice(0, -1);
    }
    const escaped = escapeRegExp(
      path,
      ESCAPE_CHARACTERS.filter(char => char !== '*')
    )
      .replace(/\*/g, '[^\\/]*');
    return new RegExp(`^${absolute ? '' : '([^\\/]+\\/)*'}${escaped}(\\/[^\\/]+)*$`, 'i');
  };
  return folderApplicationIgnoredPaths.map(processIgnoredPath);
}

function processPath (path) {
  if (!path) {
    return [];
  }
  const pathWithoutLeadingSlash = removeExtraSlash(path);
  return pathWithoutLeadingSlash
    .split('/')
    .map(path => ({path, hasPlaceholders: pathComponentHasPlaceholder(path)}))
    .reduce((r, current, idx, array) => {
      if (r.length === 0) {
        return [current];
      }
      if (
        r[r.length - 1].hasPlaceholders ||
        current.hasPlaceholders ||
        idx === array.length - 1//we should extract last component (gateway.spec)
      ) {
        return [...r, current];
      }
      const last = r.pop();
      return [
        ...r,
        {
          path: [last.path, current.path].join('/'),
          hasPlaceholders: false
        }
      ];
    }, [])
    .map((part, index, array) => ({...part, gatewaySpecFile: index === array.length - 1}))
    .map(o => new PathComponent(o));
}

function findMatchingPathsForPathComponent (
  storage,
  pathConfiguration,
  relativePath = '',
  info = {}
) {
  if (pathConfiguration.hasPlaceholders || pathConfiguration.gatewaySpecFile) {
    return new Promise((resolve) => {
      getDataStorageContents(storage, relativePath)
        .then(items => {
          const filtered = (items || [])
            .filter((item) => /^file$/i.test(item.type) === pathConfiguration.gatewaySpecFile)
            .map(item => ({
              path: removeExtraSlash(item.path),
              info: pathConfiguration.parsePathComponent(item.name)
            }))
            .filter(item => item.info)
            .map(item => ({
              ...item,
              info: {...info, ...(item.info || {})},
              storage,
              icon: pathConfiguration.gatewaySpecFile &&
                items.find(o => /^gateway.(png|jpg|tiff|jpeg|svg)$/i.test(o.name))
            }));
          resolve(filtered);
        });
    });
  } else {
    return Promise.resolve([{
      path: removeExtraSlash(`${removeExtraSlash(relativePath)}/${removeExtraSlash(pathConfiguration.path)}`),
      info: {...info}
    }]);
  }
}

function findMatchingPaths (storage, pathConfigurations, settings, appType, mapper = {}) {
  const appTypeSettings = getApplicationTypeSettings(settings, appType);
  const ignored = processIgnoredPaths(appTypeSettings);
  const pathIsIgnored = path => ignored.some(i => i.test(path));
  function iterateOverStorageContents (root, info, configurations) {
    if (configurations.length === 0) {
      return Promise.resolve([]);
    }
    if (pathIsIgnored(root)) {
      return Promise.resolve([]);
    }
    const [
      current,
      ...rest
    ] = configurations;
    return new Promise((resolve) => {
      findMatchingPathsForPathComponent(storage, current, root, info)
        .then(results => {
          if (results.length === 0) {
            return Promise.resolve([[]]);
          } else if (rest.length > 0) {
            return Promise.all(
              results.map(item => iterateOverStorageContents(item.path, item.info, rest))
            );
          } else {
            return Promise.resolve([results]);
          }
        })
        .then(results => resolve(results.reduce((r, c) => ([...r, ...c]), [])));
    });
  }
  return iterateOverStorageContents('', {...mapper}, pathConfigurations);
}

function fetchApplications (storage, user, settings, processedPath, appType, mapper) {
  return new Promise((resolve) => {
    getApplications(settings, user?.userName, appType)
      .catch((e) => {
        if (settings?.serviceUser === user?.userName) {
          console.warn(e.message);
        }
        return findMatchingPaths(storage, processedPath, settings, appType, mapper);
      })
      .then(applications => {
        return Promise.all(
          applications.map(application => readApplicationInfo(application, user, settings, appType))
        );
      })
      .then(applications => {
        resolve(applications.filter(Boolean));
      });
  });
}

function fetchFolderApplicationsByType (settings, appType, options, user) {
  const appTypeSettings = getApplicationTypeSettings(settings, appType);
  if (!appTypeSettings) {
    return Promise.resolve([]);
  }
  let owner = user;
  if (!owner && appTypeSettings.serviceUser) {
    owner = {userName: appTypeSettings.serviceUser};
  }
  if (
    options &&
    appTypeSettings &&
    appTypeSettings.folderApplicationUserPlaceholder &&
    options[appTypeSettings.folderApplicationUserPlaceholder]
  ) {
    owner = {userName: options[appTypeSettings.folderApplicationUserPlaceholder]};
  }
  const dataStorageId = parseStoragePlaceholder(appTypeSettings.appConfigStorage, owner);
  if (!Number.isNaN(Number(dataStorageId)) && appTypeSettings.appConfigPath) {
    const path = processString(
      appTypeSettings.appConfigPath,
      {
        [appTypeSettings.folderApplicationUserPlaceholder || 'user']: owner?.userName,
        ...(options || {}),
      }
    );
    const processedPath = processPath(path);
    return fetchApplications(
      dataStorageId,
      owner,
      appTypeSettings,
      processedPath,
      appType,
      options
    )
  }
  return Promise.resolve([]);
}

/**
 * @param {{options: Object?, user: Object?, force: boolean?, appType: string?}} options
 * @returns {Promise<any>}
 */
export default async function fetchFolderApplications (options = {}) {
  const {
    options: mapper,
    user,
    force = false,
    appType
  } = options;
  const settings = await fetchSettings();
  let KEY = user ? user.userName : '<SERVICE_USER>';
  if (appType) {
    KEY = `${KEY}-${appType}`;
  }
  if (!applicationsCache.has(KEY) || force) {
    const applicationTypes = appType ? [appType] : getFolderApplicationTypes(settings);
    const fetchAllAppsPromise = () => new Promise((resolve) => {
      Promise.all(
        applicationTypes.map(aType => fetchFolderApplicationsByType(
          settings,
          aType,
          mapper,
          user
        ))
      )
        .then(results => resolve(results.reduce((apps, appTypeApps) => ([...apps, ...appTypeApps]), [])));
    });
    applicationsCache.set(
      KEY,
      fetchAllAppsPromise()
    );
  }
  return applicationsCache.get(KEY);
}
