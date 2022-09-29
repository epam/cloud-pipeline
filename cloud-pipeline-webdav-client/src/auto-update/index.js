import {spawn} from 'child_process';
import electron from 'electron';
import cloudPipelineAPI from '../application/models/cloud-pipeline-api';
import getAppRoot from '../get-app-root-directory';
import {log} from '../application/models/log';

const Platforms = {
  windows: 'windows',
  macos: 'macos',
  linux: 'linux'
};

function getPlatformName () {
  if (process.platform === 'win32') {
    return Platforms.windows;
  } else if (process.platform === 'darwin') {
    return Platforms.macos;
  }
  return Platforms.linux;
}

function getUpdateScriptPath () {
  const platform = getPlatformName();
  const config = (() => {
    const cfg = (electron.remote === undefined)
      ? global.webdavClient
      : electron.remote.getGlobal('webdavClient');
    return (cfg || {}).config || {};
  })();
  const {
    updateScripts = {}
  } = config;
  const {
    [platform]: script
  } = updateScripts;
  return script;
}

async function getApplicationDistributionURL () {
  const platform = getPlatformName();
  await cloudPipelineAPI.initialize();
  const {
    [platform]: url
  } = await cloudPipelineAPI.getAppDistributionUrl();
  return url;
}

async function getSettings () {
  const platform = getPlatformName();
  log(`Auto-updating Cloud-Data application (${platform})`);
  const appDistributionUrl = await getApplicationDistributionURL();
  if (!appDistributionUrl) {
    throw new Error('Application distribution url is not found');
  }
  log(`Auto-updating Cloud-Data application (${platform}): distribution url is "${appDistributionUrl}"`);
  const script = getUpdateScriptPath();
  log(`Auto-updating Cloud-Data application (${platform}): script location is "${script || 'unknown'}"`);
  if (!script) {
    throw new Error('Auto-update script is not found');
  }
  log(`Auto-updating Cloud-Data application (${platform}): launching an auto-update process...`);
  const currentDirectory = getAppRoot();
  return {
    script,
    cwd: currentDirectory,
    url: appDistributionUrl
  }
}

async function autoUpdateWindowsApplication () {
  const {
    script,
    url,
    cwd
  } = await getSettings();
  const aProcess = spawn(
    'powershell.exe',
    ['-ExecutionPolicy', 'Bypass', '-File', `"${script}"`],
    {
      shell: true,
      detached: true,
      cwd,
      env: {
        ...process.env,
        APP_DIR: cwd,
        APP_DISTRIBUTION_URL: url
      }
    });
  aProcess.unref();
  return new Promise((resolve) => {
    aProcess.on('exit', () => resolve());
  });
}

async function autoUpdateDarwinLinuxApplication () {
  const {
    script,
    url,
    cwd
  } = await getSettings();
  const aProcess = spawn(
    'bash',
    [script],
    {
      shell: true,
      detached: true,
      cwd,
      env: {
        ...process.env,
        APP_DIR: cwd,
        APP_DISTRIBUTION_URL: url
      }
    });
  aProcess.unref();
  return new Promise((resolve, reject) => {
    aProcess.on('exit', () => resolve());
  });
}

export async function autoUpdateAvailable () {
  const url = await getApplicationDistributionURL();
  const script = getUpdateScriptPath();
  return !!url && script;
}

class AutoUpdatesChecker {
  constructor() {
    this.listeners = [];
    const config = (() => {
      const cfg = (electron.remote === undefined)
        ? global.webdavClient
        : electron.remote.getGlobal('webdavClient');
      return (cfg || {}).config || {};
    })();
    const {
      componentVersion,
      name: appName = 'Cloud Data'
    } = config;
    this.autoUpdateAvailableForOS = false;
    this.appName = appName;
    this.componentVersion = componentVersion;
    this.latestVersion = undefined;
    this.updateAvailable = false;
    (this.checkForUpdates)();
  }
  destroy () {
    this.listeners = undefined;
  }
  addEventListener (listener, check = false) {
    this.removeEventListener(listener);
    if (typeof listener === 'function') {
      this.listeners.push(listener);
    }
    if (check) {
      (this.checkForUpdates)();
    }
    this.emitInfo();
  }
  removeEventListener (listener) {
    this.listeners = this.listeners.filter(o => o !== listener);
  }
  emitEvent (payload) {
    this.listeners.forEach(aListener => aListener(payload));
  }
  emitInfo = () => {
    this.emitEvent({
      available: this.updateAvailable,
      version: this.latestVersion,
      isLatest: this.latestVersion === this.componentVersion,
      availableForOS: this.autoUpdateAvailableForOS
    });
  }
  async checkAvailableForOS () {
    try {
      await cloudPipelineAPI.initialize();
      this.autoUpdateAvailableForOS = await autoUpdateAvailable();
    } catch (exc) {
      this.autoUpdateAvailableForOS = false;
    } finally {
      this.emitInfo();
    }
  }
  checkForUpdates () {
    if (!this.checkForUpdatesPromise) {
      this.checkForUpdatesPromise = new Promise(async (resolve) => {
        log('Checking for updates...');
        const wait = (sec) => new Promise((resolve) => setTimeout(resolve, sec * 1000));
        try {
          await wait(2);
          await cloudPipelineAPI.initialize();
          await this.checkAvailableForOS();
          if (this.autoUpdateAvailableForOS) {
            this.latestVersion = await cloudPipelineAPI.getAppInfo();
            this.updateAvailable = this.latestVersion && this.latestVersion !== this.componentVersion;
            log(`Latest version ${this.latestVersion} available. Component version: ${this.componentVersion || 'unknown'}`);
            if (this.updateAvailable) {
              log(`New version ${this.latestVersion} available.`);
            }
          } else {
            this.updateAvailable = false;
            this.latestVersion = undefined;
            log(`Auto-update is not available for current OS`);
          }
        } catch (exc) {
          this.updateAvailable = false;
          this.latestVersion = undefined;
          log(`Error checking for updates: ${exc.message}`);
        } finally {
          this.emitInfo();
          this.checkForUpdatesPromise = undefined;
          resolve({available: this.updateAvailable});
        }
      });
    }
    return this.checkForUpdatesPromise;
  }
  update () {
    if (!this.updateAvailable) {
      return;
    }
    const platform = getPlatformName();
    if (platform !== Platforms.windows) {
      return autoUpdateDarwinLinuxApplication();
    } else {
      return autoUpdateWindowsApplication();
    }
  }
}

const checker = new AutoUpdatesChecker();
export default checker;
