import Commands from './commands';
import * as implementation from './implementation';

function submit (...args) {
  const [
    command,
    source,
    destination,
    progressCallback
  ] = args;
  const {fs: sourceFS, path: sourcePath, elements} = source;
  const {fs: destinationFS, path: destinationPath} = destination;
  let operation;
  switch (command) {
    case Commands.copy:
      operation = new implementation.CopyOperation(
        sourceFS,
        elements,
        destinationFS,
        destinationPath,
        progressCallback,
      );
      break;
    case Commands.move:
      operation = new implementation.MoveOperation(
        sourceFS,
        elements,
        destinationFS,
        destinationPath,
        progressCallback,
      );
      break;
    case Commands.createDirectory:
      operation = new implementation.CreateDirectoryOperation(
        sourceFS,
        sourcePath,
        progressCallback,
      );
      break;
    case Commands.delete:
      operation = new implementation.RemoveOperation(
        sourceFS,
        elements,
        progressCallback,
      );
      break;
    default:
      break;
  }
  if (operation) {
    operation.command = command;
  }
  return operation;
}

export default submit;
