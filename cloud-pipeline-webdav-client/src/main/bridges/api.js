const { ipcMain } = require('electron');
const moment = require('moment-timezone');
const ipcMessage = require('../common/ipc-message');
const CloudPipelineApi = require('../api/cloud-pipeline-api');
const displayDate = require('../common/display-date');

/**
 * @param {{configuration: Configuration}} bridgeOptions
 */
module.exports = function build(bridgeOptions) {
  const {
    configuration,
  } = bridgeOptions || {};
  if (!configuration) {
    throw new Error('Configuration is required to build bridges for api');
  }
  async function getStorages() {
    const api = new CloudPipelineApi(configuration);
    const storages = await api.getStoragesWithMetadata();
    return Promise.resolve(storages);
  }

  function davAccessInfo(value) {
    if (!value) {
      return undefined;
    }
    if (
      (typeof value === 'string' && !Number.isNaN(Number(value)))
      || typeof value === 'number'
    ) {
      const time = moment.utc(new Date(Number(value) * 1000));
      const now = moment.utc();
      return {
        available: now < time,
        expiresAt: displayDate(time, 'D MMM YYYY, HH:mm'),
      };
    }
    if (typeof value === 'boolean') {
      return {
        available: Boolean(value),
      };
    }
    return {
      available: /^true$/i.test(value),
    };
  }

  async function getStoragesWithDavAccessInfo() {
    const storages = await getStorages();
    return Promise.resolve(
      storages.map((aStorage) => {
        const {
          metadata = {},
        } = aStorage || {};
        const {
          'dav-mount': davMount,
        } = metadata;
        const info = davAccessInfo(davMount);
        return {
          ...aStorage,
          davAccessInfo: info,
        };
      }),
    );
  }

  async function requestDavAccess(storageId) {
    const api = new CloudPipelineApi(configuration);
    await api.requestDavAccess(storageId);
    return Promise.resolve(true);
  }

  ipcMain.handle('getStorages', ipcMessage(getStorages));
  ipcMain.handle('getStoragesWithDavAccessInfo', ipcMessage(getStoragesWithDavAccessInfo));
  ipcMain.handle('requestDavAccess', ipcMessage(requestDavAccess));
};
