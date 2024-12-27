const FileSystemAdapters = require("../../shared/file-system-adapters");
const adapterTypes = require('../../shared/file-system-adapters/types');

module.exports = async function initializeAdapters(configuration) {
  const fileSystemAdapters = new FileSystemAdapters(configuration);
  await fileSystemAdapters.defaultInitialize();
  const local = fileSystemAdapters.adapters.find((o) => o.type === adapterTypes.local);
  if (local) {
    local.root = process.cwd();
  }
  return fileSystemAdapters;
};
