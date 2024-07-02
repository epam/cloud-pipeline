const getFlag = require("./utilities/get-flag");
const initializeConfiguration = require('./utilities/initialize-configuration');
const printConfiguration = require('../shared/configuration/print-configuration');
const sharedLogger = require('../shared/shared-logger');

module.exports = async function config(args) {
  if (getFlag(args, '--help', '-h')) {
    console.log('config --print\t\tprints current config');
    console.log('config -p\t\tprints current config');
    console.log('config \t\t\tprints current config');
    return;
  }
  sharedLogger.verbose = getFlag(args, '--verbose');
  try {
    const config = await initializeConfiguration();
    sharedLogger.verbose = true;
    printConfiguration(config.config, 'Cloud Data configuration:');
  } catch (error) {
    console.log('Error reading configuration:', error.message);
    console.log(error);
  }
}
