import React from 'react';
import PropTypes from 'prop-types';
import useFileSystemPath, { FileSystemPathContext } from '../hooks/use-file-system-path';

function FileSystemPathProvider(
  {
    children,
  },
) {
  const fileSystemPath = useFileSystemPath();
  return (
    <FileSystemPathContext.Provider value={fileSystemPath}>
      {children}
    </FileSystemPathContext.Provider>
  );
}

FileSystemPathProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

export default FileSystemPathProvider;
