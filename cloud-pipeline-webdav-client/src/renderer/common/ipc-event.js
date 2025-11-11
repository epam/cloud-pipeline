export default function ipcEvent(event, callback) {
  if (!window.cloudDataEvents) {
    throw new Error(`Error sending "${event}" event - ipc channel is not available`);
  }
  if (typeof window.cloudDataEvents[event] !== 'function') {
    throw new Error(`Unknown event "${event}"`);
  }
  window.cloudDataEvents[event](callback);
}
