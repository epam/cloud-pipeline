import React from 'react';
import PropTypes from 'prop-types';
import useCreateFileSystem, { FileSystemContext } from '../hooks/use-file-system';

function FileSystemProvider(
  {
    children,
    index,
  },
) {
  const fileSystem = useCreateFileSystem(index);
  return (
    <FileSystemContext.Provider value={fileSystem}>
      {children}
    </FileSystemContext.Provider>
  );
}

FileSystemProvider.propTypes = {
  children: PropTypes.node.isRequired,
  index: PropTypes.number.isRequired,
};

export default FileSystemProvider;
