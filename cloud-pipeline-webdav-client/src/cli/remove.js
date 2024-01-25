const getFlag = require("./utilities/get-flag");
const getParameters = require("./utilities/get-parameters");
const getPathInfo = require("./utilities/get-path-info");
const sharedLogger = require('../shared/shared-logger');
const initializeConfiguration = require('./utilities/initialize-configuration');
const initializeAdapters = require("./utilities/initialize-adapters");
const CliDialog = require('./utilities/cli-dialog');
const Operations = require('../shared/operations');

function printHelp() {
  console.log('rm <path> [--force/-f]\tremoves directory or file');
}

module.exports = async function remove(args) {
  if (getFlag(args, '--help', '-h')) {
    printHelp();
    return;
  }
  const verbose = getFlag(args, '--verbose');
  if (verbose !== undefined) {
    sharedLogger.verbose = verbose;
  }
  const nonFlagArgs = getParameters(args);
  if (nonFlagArgs.length !== 1) {
    console.log('Wrong command');
    printHelp();
    return;
  }
  const [from] = nonFlagArgs;
  try {
    const configuration = await initializeConfiguration();
    const fileSystemAdapters = await initializeAdapters(configuration);
    const cliDialog = new CliDialog(args);
    const operations = new Operations(fileSystemAdapters, cliDialog);
    const fromInfo = await getPathInfo(from, fileSystemAdapters.adapters);
    const source = await fromInfo.adapter.createInterface();
    await operations.submit('remove', {
      source,
      sourceElements: [fromInfo.path],
    });
  } catch (error) {
    console.log('');
    console.log(error);
  }
};
