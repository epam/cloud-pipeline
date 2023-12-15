const sharedLogger = require('../shared/shared-logger');

sharedLogger.verbose = sharedLogger.Level.info;

const commands = [];

async function help() {
  commands.filter((cmd) => cmd.fn !== help).forEach((cmd) => {
    cmd.fn(['--help']);
    console.log('');
  });
}

commands.push({
  alias: ['help', '-h', '--help'],
  fn: help,
});
commands.push({
  alias: ['cp', 'copy'],
  fn: require('./copy'),
});
commands.push({
  alias: ['ls', 'list'],
  fn: require('./list'),
});
commands.push({
  alias: ['mv', 'move'],
  fn: require('./move'),
});
commands.push({
  alias: ['rm', 'remove'],
  fn: require('./remove'),
});
commands.push({
  alias: ['mkdir'],
  fn: require('./mkdir'),
});
commands.push({
  alias: ['config'],
  fn: require('./config'),
});

function findCommand(alias) {
  return commands.find((o) => o.alias.includes(alias));
}

async function cli() {
  const args = process.argv.slice(2);
  const [command] = args;
  const aCommand = findCommand(command);
  if (!aCommand) {
    if (command) {
      console.log('Unknown command:', command);
      console.log('');
    }
    await help();
    return;
  }
  await aCommand.fn(args.slice(1));
}

(cli)();
