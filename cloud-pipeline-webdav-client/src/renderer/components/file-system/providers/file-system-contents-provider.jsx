import React from 'react';
import PropTypes from 'prop-types';
import {
  useCreateFileSystemContentsStore,
  FileSystemContentsContext,
} from '../hooks/use-file-system-contents';

function FileSystemContentsProvider(
  {
    children,
  },
) {
  const contents = useCreateFileSystemContentsStore();
  return (
    <FileSystemContentsContext.Provider value={contents}>
      {children}
    </FileSystemContentsContext.Provider>
  );
}

FileSystemContentsProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

export default FileSystemContentsProvider;
