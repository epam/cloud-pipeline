export default function ipcAction(event, ...options) {
  if (!window.cloudDataActions) {
    throw new Error(`Error submitting "${event}" action`);
  }
  if (typeof window.cloudDataActions[event] !== 'function') {
    throw new Error(`Unknown action "${event}"`);
  }
  window.cloudDataActions[event](...options);
}
