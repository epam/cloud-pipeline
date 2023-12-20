const getFlag = require('./utilities/get-flag');
const getParameters = require('./utilities/get-parameters');
const getPathInfo = require('./utilities/get-path-info');
const sharedLogger = require('../shared/shared-logger');
const initializeConfiguration = require('./utilities/initialize-configuration');
const initializeAdapters = require('./utilities/initialize-adapters');
const CliDialog = require('./utilities/cli-dialog');
const Operations = require('../shared/operations');
const printOperationContinueCommand = require('./utilities/print-operation-continue-command');
const Operation = require('../shared/operations/base');

function printHelp() {
  console.log('mv <path from> <path to> [--skip/-s] [--force/-f/-r]');
}

module.exports = async function move(args) {
  if (getFlag(args, '--help', '-h')) {
    printHelp();
    return;
  }
  const verbose = getFlag(args, '--verbose');
  if (verbose !== undefined) {
    sharedLogger.verbose = verbose;
  }
  const nonFlagArgs = getParameters(args);
  if (nonFlagArgs.length !== 2) {
    console.log('Wrong command');
    printHelp();
    return;
  }
  const [from, to] = nonFlagArgs;
  let operationUuid;
  try {
    const configuration = await initializeConfiguration();
    const fileSystemAdapters = await initializeAdapters(configuration);
    const cliDialog = new CliDialog(args);
    const operations = new Operations(fileSystemAdapters, cliDialog);
    const fromInfo = await getPathInfo(from, fileSystemAdapters.adapters);
    const toInfo = await getPathInfo(to, fileSystemAdapters.adapters);
    const source = await fromInfo.adapter.createInterface();
    const destination = await toInfo.adapter.createInterface();
    const isSourceDirectory = await source.isDirectory(fromInfo.path);
    await operations.submit('move', {
      source,
      sourceElements: !toInfo.path.endsWith('/') && !isSourceDirectory ? fromInfo.path : [fromInfo.path],
      destination,
      destinationPath: toInfo.path,
      saveOperationInfo: true,
      operationInfoCallback: (uuid) => {
        operationUuid = uuid;
        printOperationContinueCommand(uuid, 'move');
      },
      iterationsCount: 1,
    }, (op) => {
      if (op.status !== Operation.Status.done && operationUuid) {
        console.log('');
        printOperationContinueCommand(operationUuid, 'move');
      }
    });
  } catch (error) {
    console.log('');
    console.log(error);
  }
};
