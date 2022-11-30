import ipcEvent from '../../common/ipc-event';

/**
 * @param {function({title: string?, message: string?}):void} callback
 */
export function registerDialogPropertiesCallback(callback) {
  ipcEvent('registerDialogPropertiesCallback', callback);
}

export function registerDialogResponseValidationCallback(callback) {
  ipcEvent('registerDialogResponseValidationCallback', callback);
}

export function dialogResponse(response) {
  ipcEvent('dialogResponse', response);
}
