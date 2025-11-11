import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import SplitPanelColumn from './column';
import { SplitPanelRowContext } from './row-context';

function mergeStyles(...styles) {
  if (styles.length === 0) {
    return undefined;
  }
  const [style, ...rest] = styles;
  const merged = {
    ...(style || {}),
    ...mergeStyles(...rest),
  };
  if (Object.keys(merged).length === 0) {
    return undefined;
  }
  return merged;
}

function SplitPanelRow(
  {
    row,
    resizerClassName,
    resizerStyle,
    panelClassName,
    panelStyle,
    children,
    resizable,
  },
) {
  return (
    <SplitPanelRowContext.Provider value={row || 0}>
      {
        React.Children.map(children, (child, index) => {
          if (child && child.type === SplitPanelColumn) {
            return React.cloneElement(
              child,
              {
                column: child.props.column || index,
                row: row || 0,
                resizerClassName,
                resizerStyle,
                className: classNames(panelClassName, child.props.className),
                style: mergeStyles(panelStyle, child.props.style),
                resizable,
              },
            );
          }
          return child;
        })
      }
    </SplitPanelRowContext.Provider>
  );
}

SplitPanelRow.propTypes = {
  row: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  // eslint-disable-next-line react/no-unused-prop-types
  height: PropTypes.number,
  resizerClassName: PropTypes.string,
  resizerStyle: PropTypes.object,
  panelClassName: PropTypes.string,
  panelStyle: PropTypes.object,
  children: PropTypes.node.isRequired,
  resizable: PropTypes.bool,
};

SplitPanelRow.defaultProps = {
  row: undefined,
  height: undefined,
  resizerClassName: undefined,
  resizerStyle: undefined,
  panelClassName: undefined,
  panelStyle: undefined,
  resizable: true,
};

export default SplitPanelRow;
