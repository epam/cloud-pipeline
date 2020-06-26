import Commands from './commands';
import * as implementation from './implementation';

function submit (mainWindow, ...args) {
  const [
    command,
    source,
    destination,
  ] = args;
  const {fs: sourceFS, path: sourcePath, elements} = source;
  const {fs: destinationFS, path: destinationPath} = destination;
  let operation;
  switch (command) {
    case Commands.copy:
      operation = new implementation.CopyOperation(
        mainWindow,
        sourceFS,
        elements,
        destinationFS,
        destinationPath,
      );
      break;
    case Commands.move:
      operation = new implementation.MoveOperation(
        mainWindow,
        sourceFS,
        elements,
        destinationFS,
        destinationPath,
      );
      break;
    case Commands.createDirectory:
      operation = new implementation.CreateDirectoryOperation(
        mainWindow,
        sourceFS,
        sourcePath,
      );
      break;
    case Commands.delete:
      operation = implementation.RemoveOperation(
        mainWindow,
        sourceFS,
        elements,
      );
      break;
    default:
      break;
  }
  let promise;
  if (operation) {
    operation.command = command;
    promise = operation.run();
  }
  return {operation, promise};
}

export default submit;
