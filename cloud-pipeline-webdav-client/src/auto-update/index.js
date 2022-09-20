import {spawn, exec} from 'child_process';
import electron from 'electron';
import cloudPipelineAPI from '../application/models/cloud-pipeline-api';
import getAppRoot from '../get-app-root-directory';
import { log } from '../application/models/log';

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

export default function autoUpdateApplication () {
  const platform = getPlatformName();
  if (platform !== Platforms.windows) {
    return autoUpdateDarwinLinuxApplication();
  } else {
    return autoUpdateWindowsApplication();
  }
}
