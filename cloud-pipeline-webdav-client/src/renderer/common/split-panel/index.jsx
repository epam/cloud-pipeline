/* eslint-disable react/no-array-index-key */
import React from 'react';
import PropTypes from 'prop-types';
import './split-panel.css';
import SplitPanelProvider from './provider';
import SplitPanelGrid from './grid';
import SplitPanelRow from './row';
import SplitPanelColumn from './column';

const fullSizeStyle = { width: '100%', height: '100%' };

function SplitPanel(
  {
    className,
    style,
    resizerClassName,
    resizerStyle,
    panelClassName,
    panelStyle,
    children,
    defaultSizes,
    resizer,
  },
) {
  return (
    <SplitPanelProvider
      className={className}
      style={style}
      sizes={defaultSizes}
      resizer={resizer}
      panelsCount={children?.length}
    >
      <SplitPanelGrid
        style={fullSizeStyle}
        resizerClassName={resizerClassName}
        resizerStyle={resizerStyle}
        panelClassName={panelClassName}
        panelStyle={panelStyle}
      >
        <SplitPanelRow row="main">
          {
            React.Children.map(children, (child, index) => React.createElement(
              SplitPanelColumn,
              {
                column: index,
              },
              child,
            ))
          }
        </SplitPanelRow>
      </SplitPanelGrid>
    </SplitPanelProvider>
  );
}

SplitPanel.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  panelClassName: PropTypes.string,
  panelStyle: PropTypes.object,
  resizerClassName: PropTypes.string,
  resizerStyle: PropTypes.object,
  children: PropTypes.node.isRequired,
  defaultSizes: PropTypes.arrayOf(PropTypes.number),
  resizer: PropTypes.number,
};

SplitPanel.defaultProps = {
  className: undefined,
  style: undefined,
  resizerClassName: undefined,
  resizerStyle: undefined,
  panelClassName: undefined,
  panelStyle: undefined,
  defaultSizes: undefined,
  resizer: 1,
};

export {
  SplitPanelGrid,
  SplitPanelColumn,
  SplitPanelRow,
  SplitPanelProvider,
};

export default SplitPanel;
