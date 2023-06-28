import combineUrl from './combine-url';
import getFetchOptions from './get-fetch-options';
import { userInfoFromToken } from './bearer-token';

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
  name: 'Default Configuration (server/user/version/app)',
  test: '^http[s]?:\\/\\/([^\\/]+)\\/([^\\/]+)\\/([^\\/]+)\\/([^?]+)(.*)?$',
  format: '^http[s]?:\\/\\/([^\\/]+)\\/([^\\/]+)\\/([^\\/]+)\\/([^?]+)(.*)?$',
  map: {
    app: '[group4]',
    redirectPathName: '[group2:uppercased]/[group3]/[group4][group5]',
    user: '[group2:uppercased]',
    version: '[group3]',
    rest: '[group5]'
  }
};

// const dashUrlParser = {
//   name: 'DASH Configuration (server/user/app)',
//   test: '^http[s]?:\\/\\/([^\\/]+)\\/([^\\/]+)\\/([^?]+)(.*)?$',
//   format: '^http[s]?:\\/\\/([^\\/]+)\\/([^\\/]+)\\/([^?]+)(.*)?$',
//   map: {
//     app: '[group3]',
//     redirectPathName: '[group2:uppercased]/[group3][group4]',
//     user: '[group2:uppercased]',
//     rest: '[group4]'
//   },
//   appType: 'dash'
// };

const defaultSettings = {
  // applicationTypes: {
  //   dash: {
  //     tag: 'app_type',
  //     tagValue: 'dash',
  //     appConfigStorage: 4393,
  //     appConfigPath: '/dash-apps/[user]/[app]/gateway.spec',
  //     folderApplicationLaunchLinkFormat: '/[user]/[app]',
  //     appConfigNodeSizes: {
  //       'dash normal': 'm5.xlarge',
  //       'dash medium': 'm5.2xlarge',
  //       'dash large': 'm5.4xlarge'
  //     },
  //     limitMounts: 'library_storage1',
  //     limitMountsPlaceholders: {
  //       library_storage1: {
  //         title: 'Dash Library',
  //         tagName: 'selectable2',
  //         tagValue: 'true',
  //         default: "464"
  //       },
  //     },
  //     folderApplicationValidation: {
  //       required: false,
  //       parameters: {
  //         CP_CAP_DASH_VALIDATOR_MODE: true
  //       },
  //     }
  //   },
  //   jupyter: {
  //     appConfigStorage: 1880,//'user_default_storage',
  //     appConfigPath: '/[user]/ShinyApps/[version]/[app]/gateway.spec',
  //   }
  // },
  darkMode: DARK_MODE,
  tag: TAG,
  tagValue: TAG_VALUE,
  tagValueRegExp: TAG_VALUE_REGEXP,
  folderAppTag: undefined,
  folderAppTagValue: undefined,
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
  applicationsMode: 'docker', //one of "docker", "folder",
  // applicationsSourceMode: 'docker', //one of "docker", "folder", "folder+docker"
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
    pollingIntervalMS: 2500,
    endpointPollingIntervalMS: 3000,
    endpointPollingMaxRequests: 5,
    endpointPollingErrorCodes: [404, 502]
  },
  anonymousAccess: {
    role: 'ROLE_ANONYMOUS_USER',
    token: undefined,
    shareWithGroups: undefined,
    originalUserNameParameter: undefined,
    anonymousAccessParameter: undefined
  },
  checkDefaultUserStorageStatus: false,
  defaultUserStorageReadOnlyWarning: undefined,
  jobContainsSensitiveStoragesWarning: undefined,
  persistSessionStateParameterName: 'CP_CAP_PERSIST_SESSION_STATE',
  customToolEndpointsEnabled: true, // true / false / { [ports]: {[count]: number, [from]: number, [to]: number} }
  endpointName: undefined,
  disablePublishingApps: false,
  redirectText: {
    withTime: 'Magellan node has been fully initialized in {SECONDS} seconds. Please wait for the application to load.',
    immediate: 'Magellan node has been fully initialized. Please wait for the application to load.'
  },
  redirectStyle: 'default', // 'white' / 'default'
  deprecatedTag: 'deprecated',
  latestTag: 'latest',
  readOnlyTag: 'readonly',
  disablePackages: {
    tag: 'disablePackages',
    parameter: 'CP_RGATEWAY_DISABLE_PACKAGES',
    warning: undefined
  },
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
          process: function (o) {
            const groupReg = new RegExp(`\\[GROUP${i}(:([^\\]]*))?\\]`, 'ig');
            const e = groupReg.exec(o);
            let replace = this.value;
            if (e && e[2]) {
              const modifiers = e[2].split(',').map(o => o.trim());
              modifiers.forEach(modifier => {
                switch (modifier.toLowerCase()) {
                  case 'lower':
                  case 'lowercase':
                  case 'lowercased':
                    replace = replace.toLowerCase();
                    break;
                  case 'upper':
                  case 'uppercase':
                  case 'uppercased':
                    replace = replace.toUpperCase();
                    break;
                }
              });
            }
            return o.replace(groupReg, replace);
          }
        });
      }
    }
    groups.push({
      value: '',
      process: function (o) {
        return o.replace(/\[group[\d]+\]/ig, this.value);
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
      if (
        /^(user|owner)$/i.test(key) &&
        result[key] &&
        result[key] !== result[key].toUpperCase()
      ) {
        result[key] = result[key].toUpperCase();
        if (verbose) {
          console.log(`URL map value uppercased for "${key}": ${result[key]}`);
        }
      }
    }
  } else if (verbose) {
    console.log('No url parsing configurations found for url', url);
  }
  return result;
}

function getApplicationTypeByUrl(url) {
  const [config] = (this.urlParser || [])
    .filter(c => (new RegExp(c.test || c.format || '', 'i')).test(url));
  if (config && url) {
    return config.appType;
  }
  return undefined;
}

defaultSettings.parseUrl = parseUrl.bind(defaultSettings);
defaultSettings.getApplicationTypeByUrl = getApplicationTypeByUrl.bind(defaultSettings);

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
  const testAnonymous = () => new Promise((resolve) => {
    if (userInfoFromToken) {
      const {
        roles = []
      } = userInfoFromToken;
      if (
        settings &&
        settings.anonymousAccess &&
        settings.anonymousAccess.token &&
        roles.length === 1 &&
        roles[0].name === settings.anonymousAccess.role
      ) {
        console.log('DETECTED ANONYMOUS USER (from bearer cookie):', userInfoFromToken);
        resolve(userInfoFromToken);
        return;
      }
    }
    resolve(undefined);
  });
  return new Promise((resolve) => {
    testAnonymous()
      .then((anonymousUser) => {
        if (anonymousUser) {
          return Promise.resolve({payload: anonymousUser});
        }
        return whoAmIRawCall(settings);
      })
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
          settings.isAnonymous = true;
          settings.originalUserName = ownerInfo?.payload?.userName;
          settings.originalUser = ownerInfo?.payload;
          return wrapWhoAmICall(settings, ownerInfo);
        } else {
          settings.isAnonymous = false;
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

function correctApplicationsMode(settings) {
  const correct = (name, o) => {
    if (/^(docker|folder|docker\+folder|folder\+docker)$/i.test(o)) {
      return o;
    }
    if (!o) {
      return undefined;
    }
    console.error(`${name}: unknown value "${o}". Supported values: "docker", "folder"`);
    return undefined;
  }
  settings.applicationsMode = correct('settings.applicationsMode', settings.applicationsMode);
  settings.applicationsSourceMode = correct('settings.applicationsSourceMode', settings.applicationsSourceMode);
  if (!settings.applicationsMode) {
    settings.applicationsMode = settings.applicationsSourceMode || 'docker';
    if (settings.applicationsSourceMode) {
      if (/^folder$/i.test(settings.applicationsSourceMode)) {
        settings.applicationsMode = 'folder';
      } else {
        settings.applicationsMode = 'docker';
      }
      console.log(
        `settings.applicationsMode is not set - using settings.applicationsSourceMode (source: "${settings.applicationsSourceMode}", mode: "${settings.applicationsMode}")`
      );
    } else {
      settings.applicationsMode = 'docker';
      console.log('settings.applicationsMode is not set - using default "docker"');
    }
  } else {
    console.log(`settings.applicationsMode: "${settings.applicationsMode}"`);
  }
  if (!settings.applicationsSourceMode) {
    settings.applicationsSourceMode = settings.applicationsMode;
    console.log(
      `settings.applicationsSourceMode is not set - using settings.applicationsMode ("${settings.applicationsMode}")`
    );
  } else {
    console.log(`settings.applicationsSourceMode: "${settings.applicationsSourceMode}"`);
  }
  if (/^folder$/i.test(settings.applicationsMode) && /^(docker|folder\+docker|docker\+folder)$/i.test(settings.applicationsSourceMode)) {
    settings.applicationsMode = 'docker';
    console.log(`"docker" applications can only be displayed in "docker" mode - switching settings.applicationsMode to "docker"`);
  }
  return settings;
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
        settings.getApplicationTypeByUrl = getApplicationTypeByUrl.bind(settings);
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
        settings.getApplicationTypeByUrl = getApplicationTypeByUrl.bind(settings);
        return Promise.resolve(settings);
      })
      .then(correctApplicationsMode)
      .then(settings => impersonateAsAnonymous(settings))
      .then(resolve)
  });
  return settingsPromise;
}
