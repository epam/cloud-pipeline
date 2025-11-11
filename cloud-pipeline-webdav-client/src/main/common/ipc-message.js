/**
 * @param {function(*):Promise<*>} messageFn
 * @returns {function():Promise<{error: string?, payload: *}>}
 */
module.exports = function ipcMessage(messageFn) {
  return async function (event, ...options) {
    try {
      const result = await messageFn(...options);
      return { error: undefined, payload: result };
    } catch (error) {
      return { error: error.message };
    }
  };
};
