import Operation from './operation';

class RemoveOperation extends Operation {
  constructor(mainWindow, sourceFS, sources) {
    super(mainWindow);
    console.log('delete', sources, 'from file system', sourceFS);
  }
}

export default RemoveOperation;
