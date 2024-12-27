import OperationTypes from './types';
import ipcResponse from '../common/ipc-response';

async function createDirectory(adapter, path) {
  await ipcResponse(
    'submitCreateDirectoryOperation',
    {
      source: adapter,
      sourcePath: path,
    },
  );
}

async function copy(sourceAdapter, elements, destinationAdapter, path) {
  await ipcResponse(
    'submitCopyOperation',
    {
      source: sourceAdapter,
      sourceElements: elements,
      destination: destinationAdapter,
      destinationPath: path,
    },
  );
}

async function move(sourceAdapter, elements, destinationAdapter, path) {
  await ipcResponse(
    'submitMoveOperation',
    {
      source: sourceAdapter,
      sourceElements: elements,
      destination: destinationAdapter,
      destinationPath: path,
    },
  );
}

async function remove(sourceAdapter, elements) {
  await ipcResponse(
    'submitRemoveOperation',
    {
      source: sourceAdapter,
      sourceElements: elements,
    },
  );
}

export {
  createDirectory,
  copy,
  move,
  remove,
  OperationTypes,
};
