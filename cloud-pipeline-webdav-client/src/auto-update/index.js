import {spawn, exec} from 'child_process';
import electron from 'electron';
import cloudPipelineAPI from '../application/models/cloud-pipeline-api';
import getAppRoot from '../get-app-root-directory';
import { log } from '../application/models/log';

async function getSettings () {
  let platform = process.platform;
  if (platform === 'win32') {
    platform = 'windows';
  } else if (platform === 'darwin') {
    platform = 'macos';
  }
  log(`Auto-updating Cloud-Data application (${platform})`);
  const currentDirectory = getAppRoot();
  await cloudPipelineAPI.initialize();
  const {
    [platform]: appDistributionUrl
  } = await cloudPipelineAPI.getAppDistributionUrl();
  if (!appDistributionUrl) {
    throw new Error('Application distribution url is not found');
  }
  log(`Auto-updating Cloud-Data application (${platform}): distribution url is "${appDistributionUrl}"`);
  const config = (() => {
    const cfg = (electron.remote === undefined)
      ? global.webdavClient
      : electron.remote.getGlobal('webdavClient');
    return (cfg || {}).config;
  })();
  const {
    updateScripts = {}
  } = config;
  const {
    [platform]: script
  } = updateScripts;
  log(`Auto-updating Cloud-Data application (${platform}): script location is "${script || 'unknown'}"`);
  if (!script) {
    throw new Error('Auto-update script is not found');
  }
  log(`Auto-updating Cloud-Data application (${platform}): launching an auto-update process...`);
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
  await cloudPipelineAPI.initialize();
  const {
    windows: windowsAppDistributionUrl,
    linux: linuxAppDistributionUrl,
    macos: macOsAppDistributionUrl
  } = await cloudPipelineAPI.getAppDistributionUrl();
  if (process.platform === 'darwin' ) {
    return !!macOsAppDistributionUrl;
  } else if (process.platform === 'linux') {
    return !!linuxAppDistributionUrl;
  } else {
    return !!windowsAppDistributionUrl;
  }
}

export default function autoUpdateApplication () {
  if (process.platform !== 'win32' ) {
    return autoUpdateDarwinLinuxApplication();
  } else {
    return autoUpdateWindowsApplication();
  }
}
