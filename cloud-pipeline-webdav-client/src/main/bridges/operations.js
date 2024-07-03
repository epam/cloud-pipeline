const { ipcMain } = require('electron');
const ipcMessage = require('../common/ipc-message');
const OperationTypes = require('../../shared/operations/types');

/**
 * @param {{operations: Operations}} bridgeOptions
 */
module.exports = function build(bridgeOptions) {
  const {
    operations,
  } = bridgeOptions || {};
  if (!operations) {
    throw new Error('Operations instance is required to build bridge for them');
  }
  const createDirectoryOperation = operations.submit.bind(
    operations,
    OperationTypes.createDirectory,
  );
  const copyOperation = operations.submit.bind(operations, OperationTypes.copy);
  const moveOperation = operations.submit.bind(operations, OperationTypes.move);
  const removeOperation = operations.submit.bind(operations, OperationTypes.remove);

  ipcMain.handle('submitCreateDirectoryOperation', ipcMessage(createDirectoryOperation));
  ipcMain.handle('submitCopyOperation', ipcMessage(copyOperation));
  ipcMain.handle('submitMoveOperation', ipcMessage(moveOperation));
  ipcMain.handle('submitRemoveOperation', ipcMessage(removeOperation));
  ipcMain.handle('abortOperation', ipcMessage(operations.abort.bind(operations)));
};
