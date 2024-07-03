const getFlag = require("./utilities/get-flag");
const getParameters = require("./utilities/get-parameters");
const getPathInfo = require("./utilities/get-path-info");
const sharedLogger = require('../shared/shared-logger');
const initializeConfiguration = require('./utilities/initialize-configuration');
const FileSystemAdapters = require("../shared/file-system-adapters");
const displaySize = require('../shared/utilities/display-size');

function printHelp() {
  console.log('ls <path>');
}

module.exports = async function list(args) {
  if (getFlag(args, '--help', '-h')) {
    printHelp();
    return;
  }
  const verbose = getFlag(args, '--verbose');
  if (verbose !== undefined) {
    sharedLogger.verbose = verbose;
  }
  let nonFlagArgs = getParameters(args);
  if (nonFlagArgs.length !== 1) {
    console.log('Wrong command');
    printHelp();
    return;
  }
  const [directory] = nonFlagArgs;
  try {
    const configuration = await initializeConfiguration();
    const fileSystemAdapters = new FileSystemAdapters(configuration);
    await fileSystemAdapters.defaultInitialize();
    const { adapter, path: relative } = await getPathInfo(directory, fileSystemAdapters.adapters);
    const adapterInterface = await adapter.createInterface();
    const res = await adapterInterface.list(relative);
    const getSortValue = (a) => a.isDirectory ? 0 : 1;
    res.sort((a, b) => getSortValue(a) - getSortValue(b));
    const longest = Math.max(0, ...res.map((r) => r.name.length));
    for (const item of res) {
      console.log(`${item.name.padEnd(longest + 1, ' ')}\t${item.isDirectory ? 'dir ' : 'file'}\t${item.isFile ? displaySize(item.size || 0) : ''}`);
    }
  } catch (error) {
    console.log('');
    console.log(error);
  }
};
