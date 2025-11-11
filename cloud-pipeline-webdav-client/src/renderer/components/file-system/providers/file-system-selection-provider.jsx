import React from 'react';
import PropTypes from 'prop-types';
import useFileSystemSelectionStore, { FileSystemSelectionContext } from '../hooks/use-file-system-selection';

function FileSystemSelectionProvider(
  {
    children,
    onChange,
  },
) {
  const selection = useFileSystemSelectionStore(onChange);
  return (
    <FileSystemSelectionContext.Provider value={selection}>
      {children}
    </FileSystemSelectionContext.Provider>
  );
}

FileSystemSelectionProvider.propTypes = {
  children: PropTypes.node.isRequired,
  onChange: PropTypes.func,
};

FileSystemSelectionProvider.defaultProps = {
  onChange: undefined,
};

export default FileSystemSelectionProvider;
