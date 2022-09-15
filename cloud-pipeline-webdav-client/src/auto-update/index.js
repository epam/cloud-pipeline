import {spawn} from 'child_process';
import electron from 'electron';
import cloudPipelineAPI from '../application/models/cloud-pipeline-api';
import getAppRoot from '../get-app-root-directory';

async function autoUpdateWindowsApplication () {
  const currentDirectory = getAppRoot();
  await cloudPipelineAPI.initialize();
  const {
    windows: appDistributionUrl
  } = await cloudPipelineAPI.getAppDistributionUrl();
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
    win: script
  } = updateScripts;
  if (!script) {
    throw new Error('Auto-update script not found');
  }
  const aProcess = spawn(
    'powershell.exe',
    ['-Command', script],
    {
      shell: true,
      detached: true,
      cwd: currentDirectory,
      env: {
        ...process.env,
        APP_DIR: currentDirectory,
        APP_DISTRIBUTION_URL: appDistributionUrl
      }
    });
  aProcess.unref();
}

async function autoUpdateDarwinApplication () {
  throw new Error('Auto-update currently not available for Darwin applications');
}

export default function autoUpdateApplication () {
  if (process.platform === 'darwin' ) {
    return autoUpdateDarwinApplication();
  } else {
    return autoUpdateWindowsApplication();
  }
}
