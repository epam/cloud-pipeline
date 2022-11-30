const fs = require('fs');
const path = require('path');
const moment = require('moment-timezone');
const clientConfigDirectory = require('../client-config-directory');

function verboseLevel(verbose, level) {
  if (typeof verbose === 'boolean') {
    return verbose;
  }
  return verbose >= level;
}

function getMessage(level, ...message) {
  return `[${moment().format('YYYY-MM-DD HH:mm:ss.SSS')} ${level}] ${message.join(' ')}`;
}

class Logger {
  static Level = {
    error: 0,
    warn: 1,
    info: 2,
    log: 3,
  };

  constructor(name = 'logs') {
    this.enabled = false;
    /**
     * @type {boolean|number}
     */
    this.verbose = true;
    const fileName = moment().format('YYYY-MM-DD').concat(`-${name}.txt`);
    this.logsFile = path.join(clientConfigDirectory, fileName);
  }

  write = (level, text) => {
    if (this.enabled) {
      try {
        const dir = path.dirname(this.logsFile);
        if (!fs.existsSync(dir)) {
          fs.mkdirSync(dir, { recursive: true });
        }
        const aMessage = (text || '')
          .split(/\r?\n/)
          .map((aLine) => getMessage(level, aLine))
          .join('\r\n')
          .concat('\r\n');
        fs.appendFileSync(this.logsFile, aMessage);
        // eslint-disable-next-line no-empty
      } catch (_) {}
    }
  };

  log = (...message) => {
    if (verboseLevel(this.verbose, Logger.Level.log)) {
      console.log(...message);
    }
    this.write('  LOG', message.join(' '));
  };

  info = (...message) => {
    if (verboseLevel(this.verbose, Logger.Level.log)) {
      console.info(...message);
    }
    this.write(' INFO', message.join(' '));
  };

  warn = (...message) => {
    if (verboseLevel(this.verbose, Logger.Level.log)) {
      console.warn(...message);
    }
    this.write(' WARN', message.join(' '));
  };

  error = (...message) => {
    if (verboseLevel(this.verbose, Logger.Level.log)) {
      console.error(...message);
    }
    this.write('ERROR', message.join(' '));
  };
}

module.exports = Logger;
