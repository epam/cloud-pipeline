const { app } = require('electron');
const EventEmitter = require('events');
const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');
const CloudPipelineAPI = require('../api/cloud-pipeline-api');
const logger = require('../common/logger');

function getCompilationAssetPath(assetName) {
  try {
    const assetFile = path.join(__dirname, assetName);
    if (fs.existsSync(assetFile)) {
      return assetFile;
    }
    // eslint-disable-next-line no-empty
  } catch (_) {}
  return undefined;
}

const updateScripts = {
  windows: getCompilationAssetPath('update-win.ps1'),
  macos: getCompilationAssetPath('update-darwin.sh'),
  linux: getCompilationAssetPath('update-linux.sh'),
};

/**
 * @param {Platform} currentPlatform
 * @returns {string}
 */
function getAppRoot(currentPlatform) {
  if (currentPlatform.isMacOS) {
    return path.dirname(path.join(app.getPath('exe'), '/../../../'));
  }
  return path.dirname(app.getPath('exe'));
}

const Platforms = {
  windows: 'windows',
  macos: 'macos',
  linux: 'linux',
};

/**
 * @param {Platform} currentPlatform
 * @returns {string}
 */
function getPlatformName(currentPlatform) {
  if (currentPlatform.isWindows) {
    return Platforms.windows;
  }
  if (currentPlatform.isMacOS) {
    return Platforms.macos;
  }
  return Platforms.linux;
}

/**
 * @param {Platform} currentPlatform
 * @returns {Promise<string|undefined>}
 */
async function getUpdateScriptPath(currentPlatform) {
  if (!currentPlatform) {
    return undefined;
  }
  if (currentPlatform.isMacOS) {
    return updateScripts.macos;
  }
  if (currentPlatform.isWindows) {
    return updateScripts.windows;
  }
  if (currentPlatform.isLinux) {
    return updateScripts.linux;
  }
  return undefined;
}

/**
 * @param {Platform} currentPlatform
 * @param {Configuration} configuration
 * @returns {Promise<undefined|*>}
 */
async function getApplicationDistributionURL(currentPlatform, configuration) {
  if (!configuration) {
    return undefined;
  }
  await configuration.initialize();
  const cloudPipelineAPI = new CloudPipelineAPI(configuration);
  try {
    const {
      windows: windowsURL,
      macos: macosURL,
      linux: linuxURL,
    } = await cloudPipelineAPI.getAppDistributionUrl();
    if (currentPlatform.isMacOS) {
      return macosURL;
    }
    if (currentPlatform.isWindows) {
      return windowsURL;
    }
    if (currentPlatform.isLinux) {
      return linuxURL;
    }
  } catch (error) {
    logger.error(`[Update checker] Error fetching app distribution url: ${error.message}`);
  }
  return undefined;
}

/**
 * @param {Platform} currentPlatform
 * @param {Configuration} configuration
 * @returns {Promise<{cwd: string, script: string, url: *}>}
 */
async function getSettings(currentPlatform, configuration) {
  if (!configuration) {
    return undefined;
  }
  await configuration.initialize();
  const platform = getPlatformName(currentPlatform);
  logger.log(`[Update checker] Auto-updating Cloud-Data application (${platform})`);
  const appDistributionUrl = await getApplicationDistributionURL(currentPlatform, configuration);
  if (!appDistributionUrl) {
    throw new Error('Application distribution url is not found');
  }
  logger.log(`[Update checker] Auto-updating Cloud-Data application (${platform}): distribution url is "${appDistributionUrl}"`);
  const script = await getUpdateScriptPath(currentPlatform);
  logger.log(`[Update checker] Auto-updating Cloud-Data application (${platform}): script location is "${script || 'unknown'}"`);
  if (!script) {
    throw new Error('Auto-update script is not found');
  }
  logger.log(`[Update checker] Auto-updating Cloud-Data application (${platform}): launching an auto-update process...`);
  const currentDirectory = getAppRoot(currentPlatform);
  return {
    script,
    cwd: currentDirectory,
    url: appDistributionUrl,
  };
}

/**
 * @param {{script: string, url: string, cwd: string}} options
 * @returns {Promise<unknown>}
 */
async function autoUpdateWindowsApplication(options) {
  const {
    script,
    url,
    cwd,
  } = options || {};
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
        APP_DISTRIBUTION_URL: url,
      },
    },
  );
  aProcess.unref();
  return new Promise((resolve) => {
    aProcess.on('exit', () => resolve());
  });
}

/**
 * @param {{script: string, url: string, cwd: string}} options
 * @returns {Promise<unknown>}
 */
async function autoUpdateDarwinLinuxApplication(options) {
  const {
    script,
    url,
    cwd,
  } = options || {};
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
        APP_DISTRIBUTION_URL: url,
      },
    },
  );
  aProcess.unref();
  return new Promise((resolve) => {
    aProcess.on('exit', () => resolve());
  });
}

/**
 * @param {Platform} currentPlatform
 * @param {Configuration} configuration
 * @returns {Promise<boolean>}
 */
async function autoUpdateAvailable(currentPlatform, configuration) {
  if (!configuration) {
    return false;
  }
  await configuration.initialize();
  const url = await getApplicationDistributionURL(currentPlatform, configuration);
  const script = await getUpdateScriptPath(currentPlatform);
  return !!url && !!script;
}

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const RECHECK_INTERVAL = 5 * MINUTE;

class AutoUpdatesChecker extends EventEmitter {
  /**
   * @param {Configuration} configuration
   * @param {Platform} currentPlatform
   */
  constructor(configuration, currentPlatform) {
    if (!configuration) {
      throw new Error('Auto-update checker should be initialized with configuration');
    }
    if (!currentPlatform) {
      throw new Error('Auto-update checker should be initialized with current platform');
    }
    super();
    this.configuration = configuration;
    this.currentPlatform = currentPlatform;
    configuration.on('reload', () => this.checkForUpdates(true));
    this.latestVersion = undefined;
    this.autoUpdateAvailableForOS = false;
    this.updateAvailable = false;

    const interval = () => {
      this.checkForUpdates()
        .then(() => setTimeout(interval, RECHECK_INTERVAL));
    };
    interval();
  }

  /**
   * Returns available version for update
   * @returns {string}
   */
  get available() {
    return this.updateAvailable && this.autoUpdateAvailableForOS
      ? this.latestVersion
      : undefined;
  }

  get supported() {
    return this.autoUpdateAvailableForOS;
  }

  async checkAvailableForOS() {
    try {
      this.autoUpdateAvailableForOS = await autoUpdateAvailable(
        this.currentPlatform,
        this.configuration,
      );
    } catch (exc) {
      this.autoUpdateAvailableForOS = false;
    }
  }

  checkForUpdates(force = false) {
    if (!this.checkForUpdatesPromise || force) {
      const routine = async () => {
        logger.log('[Update checker] Checking for updates...');
        try {
          await this.configuration.initialize();
          const { componentVersion } = this.configuration;
          const cloudPipelineAPI = new CloudPipelineAPI(this.configuration);
          await this.checkAvailableForOS();
          if (this.autoUpdateAvailableForOS) {
            this.latestVersion = await cloudPipelineAPI.getAppInfo();
            this.updateAvailable = this.latestVersion
              && this.latestVersion !== componentVersion;
            logger.log(`[Update checker] Version ${this.latestVersion} available. Component version: ${componentVersion || 'unknown'}`);
            if (this.updateAvailable) {
              logger.log(`[Update checker] New version ${this.latestVersion} available for auto-update.`);
            }
          } else {
            this.updateAvailable = false;
            this.latestVersion = undefined;
            logger.log('[Update checker] Auto-update is not available for current OS');
          }
        } catch (exc) {
          this.updateAvailable = false;
          this.latestVersion = undefined;
          logger.error(`[Update checker] Error checking for updates: ${exc.message}`);
        } finally {
          this.emit(
            'auto-update-info',
            {
              available: this.available,
              supported: this.supported,
            },
          );
        }
        return {
          available: this.available,
          supported: this.supported,
        };
      };
      this.checkForUpdatesPromise = new Promise((resolve) => {
        routine()
          .then((result) => {
            resolve(result);
            this.checkForUpdatesPromise = undefined;
          });
      });
    }
    return this.checkForUpdatesPromise;
  }

  async update() {
    if (!this.updateAvailable || !this.configuration) {
      return;
    }
    await this.configuration.initialize();
    const platform = getPlatformName(this.currentPlatform);
    const settings = await getSettings(this.currentPlatform, this.configuration);
    if (!settings) {
      throw new Error('Cannot update application. Update script and url are missing');
    }
    if (platform !== Platforms.windows) {
      await autoUpdateDarwinLinuxApplication(settings);
    }
    await autoUpdateWindowsApplication(settings);
  }
}

module.exports = AutoUpdatesChecker;
