const Logger = require('./logger');

const shared = new Logger();
shared.Level = Logger.Level;

module.exports = shared;
