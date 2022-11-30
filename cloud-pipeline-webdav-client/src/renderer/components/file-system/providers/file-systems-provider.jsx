import React from 'react';
import PropTypes from 'prop-types';
import useCreateFileSystems, { FileSystemsContext } from '../hooks/use-file-systems';

function FileSystemsProvider(
  {
    children,
  },
) {
  const store = useCreateFileSystems();
  return (
    <FileSystemsContext.Provider value={store}>
      {children}
    </FileSystemsContext.Provider>
  );
}

FileSystemsProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

export default FileSystemsProvider;
