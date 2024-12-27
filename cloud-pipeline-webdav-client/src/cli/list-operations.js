const getFlag = require('./utilities/get-flag');
const getParameters = require('./utilities/get-parameters');
const sharedLogger = require('../shared/shared-logger');
const initializeConfiguration = require('./utilities/initialize-configuration');
const Operation = require('../shared/operations/base');
const Operations = require('../shared/operations');
const CliDialog = require('./utilities/cli-dialog');
const initializeAdapters = require('./utilities/initialize-adapters');
const printOperationContinueCommand = require('./utilities/print-operation-continue-command');

function printHelp() {
  console.log('operations\t\t\t prints list of not completed operations');
  console.log('operations <uuid>\t\t print operation info');
  console.log('operations <uuid> --continue [--force/-f] [--skip/-s]\t continue operation execution');
}

module.exports = async function listOperations(args) {
  if (getFlag(args, '--help', '-h')) {
    printHelp();
    return;
  }
  const verbose = getFlag(args, '--verbose');
  if (verbose !== undefined) {
    sharedLogger.verbose = verbose;
  }
  const nonFlagArgs = getParameters(args);
  if (nonFlagArgs.length === 0) {
    const ids = await Operation.getOperationInfoIdentifiers();
    console.log('Not completed operations:');
    ids.forEach((id) => console.log(id));
    return;
  }
  try {
    const configuration = await initializeConfiguration();
    const fileSystemAdapters = await initializeAdapters(configuration);
    const cliDialog = new CliDialog(args);
    const operations = new Operations(fileSystemAdapters, cliDialog);
    if (nonFlagArgs.length === 1 && !getFlag(args, '--continue')) {
      const op = await operations.recover(nonFlagArgs[0]);
      console.log('');
      switch (op.operationType) {
        case 'copy':
        case 'move': {
          console.log('Type:       ', op.operationType);
          console.log('Source:     ', typeof op.source === 'string' ? op.source : op.source.adapter.identifier);
          console.log('Destination:', typeof op.destination === 'string' ? op.destination : op.destination.adapter.identifier);
          console.log('');
          console.log('Items:', op.list.length);
          const filtered = op.list.filter((i) => i.isFile);
          for (let i = 0; i < filtered.length; i += 1) {
            console.log(filtered[i].from, op.completed.includes(filtered[i].from) ? 'COPIED' : '');
          }
          break;
        }
        default:
          console.log('Type:       ', op.operationType);
          break;
      }
    } else if (nonFlagArgs.length === 1 && getFlag(args, '--continue')) {
      const result = await operations.recoverAndSubmit(nonFlagArgs[0], { iterationsCount: 1 });
      if (result.status !== Operation.Status.done) {
        console.log('');
        printOperationContinueCommand(nonFlagArgs[0], result.operationType);
      }
    } else {
      printHelp();
    }
  } catch (error) {
    console.log('');
    console.log(error);
  }
};
