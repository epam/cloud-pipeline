import combineUrl from './combine-url';
import getFetchOptions from './get-fetch-options';

const ANONYMOUS = Symbol('anonymous user');
const ORIGINAL_USER_NAME = Symbol('original user name');
const ORIGINAL_USER = Symbol('original user');

function getSettings() {
  return new Promise((resolve, reject) => {
    try {
      fetch(
        '/settings',
        {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        }
      )
        .then(response => {
          const codeFamily = Math.ceil(response.status / 100);
          if (codeFamily === 4 || codeFamily === 5) {
            reject(new Error(`Response: ${response.status} ${response.statusText}`));
          } else {
            response
              .json()
              .then(resolve)
              .catch(reject);
          }
        })
        .catch(reject);
    } catch (e) {
      reject(e);
    }
  });
}

const TAG = CP_APPLICATIONS_TAG || 'app_type';
const TAG_VALUE = document.location.hostname;
const TAG_VALUE_REGEXP = new RegExp(`^${TAG_VALUE}$`, 'i');
const API = CP_APPLICATIONS_API || '/restapi';

let settings;
let settingsPromise;

const defaultUrlParser = {
  name: 'Default Configuration (server/user/app)',
  test: '^http[s]?:\\/\\/([^\\/]+)\\/([^\\/]+)\\/([^?]+)(.*)?$',
  format: '^http[s]?:\\/\\/([^\\/]+)\\/([^\\/]+)\\/([^?]+)(.*)?$',
  map: {
    app: '[group4]',
    redirectPathName: '[group2]/[group3][group4]',
    user: '[group2]',
    version: '[group3]',
    rest: '[group4]'
  }
};

const defaultSettings = {
  tag: TAG,
  tagValue: TAG_VALUE,
  tagValueRegExp: TAG_VALUE_REGEXP,
  initialPollingDelay: INITIAL_POLLING_DELAY || POLLING_INTERVAL || 1000,
  pollingInterval: POLLING_INTERVAL || 1000,
  limitMounts: LIMIT_MOUNTS || 'default',
  // limitMountsPlaceholders: {
  //   library_storage: {
  //     title: 'Library',
  //     tagName: 'selectable',
  //     tagValue: 'true',
  //     default: "464"
  //   },
  // },
  placeholderDependenciesTagName: 'lib_dependencies',
  api: API,
  supportName: CP_APPLICATIONS_SUPPORT_NAME || 'support team',
  useParentNodeId: USE_PARENT_NODE_ID,
  showTimer: SHOW_TIMER,
  prettyUrlDomain: PRETTY_URL_DOMAIN,
  prettyUrlPath: PRETTY_URL_PATH,
  urlParser: [defaultUrlParser],
  parameters: {},
  useBearerAuth: true,
  redirectBehavior: 'endpoint',// ['endpoint', 'custom']
  redirectUrl: undefined,
  shareWithUsers: undefined,
  shareWithGroups: undefined,
  redirectOnAPIUnauthenticated: false,
  checkRunPrettyUrl: true,
  redirectAfterTaskFinished: undefined,
  appConfigStorage: 1880,//'user_default_storage',
  appConfigPath: '/[user]/ShinyApps/[version]/[app]/gateway.spec',
  folderApplicationIgnoredPaths: [],
  folderApplicationUserPlaceholder: 'user', // to determine which placeholder is used for user name substitution
  folderApplicationAppPlaceholder: 'app', // to determine which placeholder is used for application name substitution
  folderApplicationPathAttributes: {
    version: 'RVersion',
    app: 'name'
  },
  appConfigNodeSizes: {
    normal: 'm5.xlarge',
    medium: 'm5.2xlarge',
    large: 'm5.4xlarge'
  },
  sessionInfoStorage: undefined,
  sessionInfoPath: undefined,
  userStoragesAttribute: undefined,
  applicationsMode: 'folder', //one of "docker", "folder",
  serviceUser: "PIPE_ADMIN",
  folderApplicationLaunchLinkFormat: '/[user]/[version]/[app]',
  folderApplicationAdvancedUserRoleName: ['ROLE_ADVANCED_USER'],
  folderApplicationsListStorage: undefined,
  folderApplicationsListFile: undefined,//'/[user]/ShinyApps/applications.spec',
  folderApplicationRequiredFields: ['description', 'fullDescription', 'icon'],
  help: undefined,
  folderApplicationValidation: {
    required: false,
    applicationPath: '/cloud-data/[application_storage_path]/[application_path]',
    expiresAfter: '130m',
    parameters: {
      CP_CAP_SHINY_VALIDATOR_MODE: true
    },
    pollingIntervalMS: 2500
  },
  anonymousAccess: {
    role: 'ROLE_ANONYMOUS_USER',
    token: undefined,
    shareWithGroups: undefined,
    originalUserNameParameter: undefined
  },
  checkDefaultUserStorageStatus: false,
  defaultUserStorageReadOnlyWarning: undefined
};

function parseUrl(url, verbose = false) {
  const result = {};
  if (verbose) {
    console.log('Configurations:', this.urlParser || []);
    console.log('Configurations:', JSON.stringify(this.urlParser || []));
  }
  const [config] = (this.urlParser || [])
    .filter(c => (new RegExp(c.test || c.format || '', 'i')).test(url));
  if (config && url) {
    if (verbose) {
      console.log(`${config.name || config.format} configuration will be used for url ${url}`, config);
    }
    const reg = new RegExp(config.format, 'i');
    const groups = [];
    const exec = reg.exec(url);
    if (exec && exec.length) {
      for (let i = 1; i < exec.length; i++) {
        groups.push({
          value: exec[i] || '',
          reg: new RegExp(`\\[GROUP${i}\\]`, 'ig'),
          process: function (o) {
            return o.replace(this.reg, this.value);
          }
        });
      }
    }
    groups.push({
      value: '',
      reg: /\[group[\d]+\]/ig,
      process: function (o) {
        return o.replace(this.reg, this.value);
      }
    })
    const keys = Object.keys(config.map || {});
    for (let k = 0; k < keys.length; k++) {
      const key = keys[k];
      const value = config.map[key];
      if (typeof value === 'string') {
        result[key] = groups.reduce((r, c) => c.process(r), value);
      } else {
        result[key] = value;
      }
    }
  } else if (verbose) {
    console.log('No url parsing configurations found for url', url);
  }
  return result;
}

defaultSettings.parseUrl = parseUrl.bind(defaultSettings);

function mergeSettings (...o) {
  if (o.length === 0) {
    return {};
  }
  const result = Object.assign({}, o[0]);
  for (let i = 1; i < o.length; i++) {
    const s = o[i];
    const keys = Object.keys(s);
    for (let k = 0; k < keys.length; k++) {
      if (s.hasOwnProperty(keys[k]) && s[keys[k]] !== undefined) {
        result[keys[k]] = s[keys[k]];
      }
    }
  }
  return result;
}

function safeMergeSettings (...o) {
  return mergeSettings(...o.filter(Boolean));
}

function whoAmIRawCall (settings) {
  return new Promise((resolve, reject) => {
    try {
      fetch(
        combineUrl(settings.api, `/whoami`),
        {
          ...getFetchOptions(settings),
          body: undefined,
          method: 'GET'
        }
      )
        .then(response => {
          const codeFamily = Math.ceil(response.status / 100);
          if (codeFamily === 4 || codeFamily === 5) {
            reject(new Error(`Response: ${response.status} ${response.statusText}`));
          } else {
            response
              .json()
              .then(resolve)
              .catch(reject);
          }
        })
        .catch(reject);
    } catch (e) {
      reject(e);
    }
  });
}

function extendUrlMapWithOwner(maps, owner) {
  if (!owner) {
    return;
  }
  maps.forEach(map => {
    if (map.map) {
      if (!map.map.hasOwnProperty('owner')) {
        map.map.owner = owner.userName;
        console.log(map.name || map.format, `map was extended with "owner"="${owner.userName}" property`);
      } else {
        console.log(map.name || map.format, 'map already has "owner" property');
      }
      if (!map.map.hasOwnProperty('owner_id')) {
        map.map.owner_id = `${owner.id}`;
        console.log(map.name || map.format, `map was extended with "owner_id"="${owner.id}" property`);
      } else {
        console.log(map.name || map.format, 'map already has "owner_id" property');
      }
    }
  });
}

function impersonateAsAnonymous(settings) {
  const wrapWhoAmICall = (o, initial) => new Promise((resolve, reject) => {
    whoAmIRawCall(o)
      .then(() => resolve(initial))
      .catch(reject);
  });
  return new Promise((resolve) => {
    whoAmIRawCall(settings)
      .then(ownerInfo => {
        const {
          roles = []
        } = ownerInfo?.payload || {};
        if (
          settings &&
          settings.anonymousAccess &&
          settings.anonymousAccess.token &&
          roles.some(role => role.name === settings.anonymousAccess.role)
        ) {
          console.warn('ANONYMOUS USER');
          settings[ANONYMOUS] = true;
          settings[ORIGINAL_USER_NAME] = ownerInfo?.payload?.userName;
          settings[ORIGINAL_USER] = ownerInfo?.payload;
          return wrapWhoAmICall(settings, ownerInfo);
        }
        return Promise.resolve(ownerInfo);
      })
      .catch((e) => {
        console.log('Error fetching user info:', e.message);
      })
      .then((info) => {
        extendUrlMapWithOwner(settings.urlParser || [], info?.payload);
        console.log('The following settings will be used (env vars):', settings);
        resolve(settings);
      });
  });
}

export default function fetchSettings() {
  if (settings) {
    return Promise.resolve(settings);
  }
  if (settingsPromise) {
    return settingsPromise;
  }
  settingsPromise = new Promise((resolve) => {
    getSettings()
      .then(result => {
        console.log('Settings response:', JSON.stringify(result));
        settings = safeMergeSettings(
          {},
          defaultSettings,
          (result || {})[document.location.hostname]);
        settings.tagValueRegExp = new RegExp(`^${settings.tagValue}$`, 'i')
        settings.parseUrl = parseUrl.bind(settings);
        if ((settings.urlParser || []).indexOf(defaultUrlParser) === -1) {
          if (Array.isArray(settings.urlParser)) {
            settings.urlParser.push(defaultUrlParser);
          }
        }
        return Promise.resolve(settings);
      })
      .catch((e) => {
        console.log('Error fetching settings from API:');
        console.error(e);
        settings = mergeSettings(defaultSettings);
        settings.parseUrl = parseUrl.bind(settings);
        return Promise.resolve(settings);
      })
      .then(settings => {
        Object.defineProperty(settings, 'isAnonymous', {
          get () {
            return !!(this[ANONYMOUS]);
          }
        });
        Object.defineProperty(settings, 'originalUserName', {
          get () {
            return this[ORIGINAL_USER_NAME];
          }
        });
        Object.defineProperty(settings, 'originalUser', {
          get () {
            return this[ORIGINAL_USER];
          }
        });
        return Promise.resolve(settings);
      })
      .then(settings => impersonateAsAnonymous(settings))
      .then(resolve)
  });
  return settingsPromise;
}
