import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {
  SplitPanelProvider,
} from '../../common/split-panel';
import './file-system-contents.css';
import FileSystemItemsHeader from './file-system-items-header';
import FileSystemItemsTable from './file-system-items-table';
import { columns, sizes } from './utilities/columns';

function FileSystemContents(
  {
    className,
    style,
  },
) {
  return (
    <SplitPanelProvider
      className={
        classNames(
          className,
          'directory-contents-container',
        )
      }
      style={style}
      resizer={5}
      panelsCount={3}
      sizes={sizes}
      columns={columns}
    >
      <FileSystemItemsHeader />
      <FileSystemItemsTable />
    </SplitPanelProvider>
  );
}

FileSystemContents.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

FileSystemContents.defaultProps = {
  className: undefined,
  style: {},
};

export default FileSystemContents;
