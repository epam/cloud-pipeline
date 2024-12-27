module.exports = function printOperationContinueCommand(uuid, type = 'copy') {
  console.log('');
  console.log(`${type} operation identifier:`, uuid);
  console.log('');
  console.log('if operation will be aborted, you can continue execution using command:');
  console.log(`<cloud-data-cli> operations ${uuid} --continue`);
  console.log('');
};
