import parseStoragePlaceholder from './parse-storage-placeholder';
import processString from './process-string';
import getDataStorageContents from './cloud-pipeline-api/data-storage-contents';
import fetchFolderApplicationInfo from './fetch-folder-application-info';
import combineUrl from './base/combine-url';
import {getApplications} from './folder-applications-list';
import {ESCAPE_CHARACTERS, escapeRegExp} from './utilities/escape-reg-exp';
import PathComponent, {pathComponentHasPlaceholder} from './utilities/path-component';
import removeExtraSlash from './utilities/remove-slashes';
import readApplicationInfo from './folder-applications/read-application-info';

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
    .reduce((r, current) => {
      if (r.length === 0) {
        return [current];
      }
      if (r[r.length - 1].hasPlaceholders || current.hasPlaceholders) {
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

function findMatchingPaths (storage, pathConfigurations, settings) {
  const ignored = processIgnoredPaths(settings);
  const pathIsIgnored = path => ignored.some(i => i.test(path));
  function iterateOverStorageContents (root, info, configurations) {
    if (configurations.length === 0) {
      return Promise.resolve([]);
    }
    if (pathIsIgnored(root)) {
      console.log('ignore path', root);
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
  return iterateOverStorageContents('', {}, pathConfigurations);
}

function fetchApplications (storage, user, settings, processedPath) {
  return new Promise((resolve) => {
    getApplications(settings, user?.userName)
      .catch((e) => {
        if (settings?.serviceUser === user?.userName) {
          console.warn(e.message);
        }
        return findMatchingPaths(storage, processedPath, settings);
      })
      .then(applications => {
        return Promise.all(
          applications.map(application => readApplicationInfo(application, user, settings))
        );
      })
      .then(applications => {
        resolve(applications.filter(Boolean));
      });
  });
}

export default function fetchFolderApplications (settings, options, user, force = false) {
  if (!applicationsCache.has(user.userName) || force) {
    const dataStorageId = parseStoragePlaceholder(settings.appConfigStorage, user);
    if (!Number.isNaN(Number(dataStorageId)) && settings.appConfigPath) {
      const path = processString(
        settings.appConfigPath,
        {
          ...(options || {}),
          [settings.folderApplicationUserPlaceholder || 'user']: user.userName
        }
      );
      const processedPath = processPath(path);
      applicationsCache.set(
        user.userName,
        fetchApplications(dataStorageId, user, settings, processedPath)
      );
    } else {
      applicationsCache.set(
        user.userName,
        Promise.resolve([])
      );
    }
  }
  return applicationsCache.get(user.userName);
}
