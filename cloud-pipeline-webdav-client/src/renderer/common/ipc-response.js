export default async function ipcResponse(event, ...options) {
  if (!window.cloudData) {
    throw new Error(`Error retrieving response for "${event}"`);
  }
  if (typeof window.cloudData[event] !== 'function') {
    throw new Error(`Unknown event "${event}"`);
  }
  const response = await window.cloudData[event](...options);
  if (response.error) {
    throw new Error(response.error);
  }
  return response.payload;
}
