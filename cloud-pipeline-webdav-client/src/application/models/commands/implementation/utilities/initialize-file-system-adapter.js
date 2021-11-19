import {initializeFileSystem} from '../../../file-systems';

function initializeFileSystemAdapter (adapterType) {
  if (!adapterType) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const impl = initializeFileSystem(adapterType);
    if (!impl) {
      reject({message: `Unknown type of file system: ${adapterType}`});
    } else {
      impl.initialize()
        .then(() => resolve(impl))
        .catch(message => reject({message}));
    }
  });
}

export default initializeFileSystemAdapter;
