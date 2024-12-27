const getFlag = require("./utilities/get-flag");
const getParameters = require("./utilities/get-parameters");
const getPathInfo = require("./utilities/get-path-info");
const sharedLogger = require('../shared/shared-logger');
const initializeConfiguration = require('./utilities/initialize-configuration');
const initializeAdapters = require("./utilities/initialize-adapters");
const CliDialog = require('./utilities/cli-dialog');
const Operations = require('../shared/operations');

function printHelp() {
  console.log('mkdir <path from> [--force/-f]');
}

module.exports = async function mkdir(args) {
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
  const [dir] = nonFlagArgs;
  try {
    const configuration = await initializeConfiguration();
    const fileSystemAdapters = await initializeAdapters(configuration);
    const cliDialog = new CliDialog(args);
    const operations = new Operations(fileSystemAdapters, cliDialog);
    const fromInfo = await getPathInfo(dir, fileSystemAdapters.adapters);
    await operations.submit('create directory', {
      source: fromInfo.adapter,
      sourcePath: fromInfo.path,
      isNewDirectoryPath: true,
    });
  } catch (error) {
    console.log('');
    console.log(error);
  }
};
