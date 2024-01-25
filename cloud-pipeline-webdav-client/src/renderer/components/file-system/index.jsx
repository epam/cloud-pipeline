import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import FileSystemContents from './file-system-contents';
import FileSystemActions from './file-system-actions';
import FileSystemPath from './file-system-path';
import { useFileSystemActive } from './hooks/use-file-system';
import FileSystemPathProvider from './providers/file-system-path-provider';
import FileSystemContentsProvider from './providers/file-system-contents-provider';
import FileSystemSelectionProvider from './providers/file-system-selection-provider';
import FileSystemProvider from './providers/file-system-provider';
import FileSystemsProvider from './providers/file-systems-provider';
import './file-system.css';

function FileSystemLayout(
  {
    className,
    style,
  },
) {
  const active = useFileSystemActive();
  return (
    <div
      className={
        classNames(
          className,
          'file-system-tab-container',
          {
            active,
          },
        )
      }
      style={style}
    >
      <FileSystemActions className="file-system-tab-header" />
      <FileSystemPath />
      <FileSystemContents />
    </div>
  );
}

FileSystemLayout.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

FileSystemLayout.defaultProps = {
  className: undefined,
  style: undefined,
};

function FileSystem(
  {
    className,
    style,
    index,
  },
) {
  return (
    <FileSystemProvider index={index}>
      <FileSystemPathProvider>
        <FileSystemContentsProvider>
          <FileSystemSelectionProvider>
            <FileSystemLayout
              className={className}
              style={style}
            />
          </FileSystemSelectionProvider>
        </FileSystemContentsProvider>
      </FileSystemPathProvider>
    </FileSystemProvider>
  );
}

FileSystem.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  index: PropTypes.number.isRequired,
};

FileSystem.defaultProps = {
  className: undefined,
  style: {},
};

export { FileSystemsProvider };
export default FileSystem;
