import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
import FileSystem, { FileSystemsProvider } from '../file-system';
import SplitPanel from '../../common/split-panel';
import './file-systems.css';

function useTabs(count = 2) {
  return useMemo(
    () => (new Array(count)).fill('').map((o, idx) => idx),
    [count],
  );
}

function FileSystems(
  {
    className,
    style,
    tabClassName,
    tabStyle,
  },
) {
  const tabs = useTabs();
  return (
    <FileSystemsProvider>
      <SplitPanel
        className={className}
        resizer={8}
        panelClassName={tabClassName}
        panelStyle={tabStyle}
        style={style}
      >
        {
          tabs.map((tabIndex) => (
            <FileSystem index={tabIndex} key={`tab-${tabIndex}`} />
          ))
        }
      </SplitPanel>
    </FileSystemsProvider>
  );
}

FileSystems.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tabClassName: PropTypes.string,
  tabStyle: PropTypes.object,
};

FileSystems.defaultProps = {
  className: undefined,
  style: undefined,
  tabClassName: undefined,
  tabStyle: undefined,
};

export default FileSystems;
