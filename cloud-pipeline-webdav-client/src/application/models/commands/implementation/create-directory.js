import Operation from './operation';

class CreateDirectoryOperation extends Operation {
  constructor(mainWindow, fileSystem, parentDirectory) {
    super(mainWindow);
    console.log('create directory in', parentDirectory, 'in file system', fileSystem);
  }
}

export default CreateDirectoryOperation;
