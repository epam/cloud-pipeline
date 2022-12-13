module.exports = function makeDelayedLog(log, timeout = 1000) {
  let lastMessage;
  let timer;
  let initialized = false;

  function flush() {
    clearTimeout(timer);
    timer = undefined;
    if (lastMessage && typeof log === 'function') {
      log(...lastMessage);
      lastMessage = undefined;
    }
  }

  function write() {
    initialized = true;
    flush();
    timer = setTimeout(write, timeout);
  }

  function doLog(...message) {
    lastMessage = [
      ...message,
      ` - (delayed log with interval ${timeout}ms)`,
    ];
    if (!initialized) {
      write();
    }
  }

  return {
    log: doLog,
    flush,
  };
};
