const fs = require('fs');

const MAINTENANCE_MODE_FLAG_FILE = '/etc/maintenance/active';
const CHECK_FILE_TIMEOUT_MS = 1000; // 1sec

module.exports = function maintenance() {
    let active = false;
    let handle;
    const listeners = [];
    const addListener = (listener) => {
      listeners.push(listener);
      if (active) {
          listener(active);
      }
      return () => listeners.splice(listeners.indexOf(listener), 1);
    };
    const iteration = () => {
        const flagExists = fs.existsSync(MAINTENANCE_MODE_FLAG_FILE);
        if (flagExists !== active) {
            active = flagExists;
            console.log(`${new Date()} Maintenance mode: ${active ? 'on' : 'off'}`);
            listeners.forEach(listener => listener(active));
        }
        handle = setTimeout(iteration, CHECK_FILE_TIMEOUT_MS);
    };
    iteration();
    return {
        addListener,
        stop: () => clearTimeout(handle)
    };
}
