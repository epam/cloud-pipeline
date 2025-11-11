const buildAdaptersBridge = require('./adapters');
const buildApiBridge = require('./api');
const buildAutoUpdateBridge = require('./auto-update');
const buildConfigurationBridge = require('./configuration');
const buildOperationsBridge = require('./operations');
const buildPlatformBridge = require('./platform');

/**
 * @typedef {object} BridgeOptions
 * @property {FileSystemAdapters} adapters
 * @property {Configuration} configuration
 * @property {AutoUpdatesChecker} autoUpdateChecker
 * @property {Operations} operations
 * @property {Platform} currentPlatform
 */

/**
 * @param {BridgeOptions} bridgeOptions
 */
module.exports = function buildBridges(bridgeOptions) {
  buildAdaptersBridge(bridgeOptions);
  buildApiBridge(bridgeOptions);
  buildAutoUpdateBridge(bridgeOptions);
  buildConfigurationBridge(bridgeOptions);
  buildOperationsBridge(bridgeOptions);
  buildPlatformBridge(bridgeOptions);
};
