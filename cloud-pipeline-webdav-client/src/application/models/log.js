const moment = require('moment-timezone');
const path = require('path');
const fs = require('fs');
const electron = require('electron');

export function log(message) {
  let logsEnabled = false;
  if (electron.remote === undefined) {
    logsEnabled = global.logsEnabled;
  } else {
    logsEnabled = electron.remote.getGlobal('logsEnabled');
  }
  const messageWithDate = `[${moment.utc().format('YYYY-MM-DD HH:mm:ss')}]: ${message}`;
  if (logsEnabled) {
    const fileName = `${moment.utc().format('YYYY-MM-DD')}-log.txt`;
    const logFileName = path.join(require('os').homedir(), '.pipe-webdav-client', fileName);
    try {
      fs.appendFileSync(logFileName, `${messageWithDate}\r\n`);
    } catch (e) {
    }
  }
  console.log(messageWithDate);
}

export function error(e) {
  log(`ERROR: ${e && e.message ? e.message : e}`);
}
