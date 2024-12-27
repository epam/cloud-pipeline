/* eslint-disable react/jsx-props-no-spreading */
import React, { useMemo } from 'react';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import { useGridStyle } from './context';
import SplitPanelRow from './row';

function useRows(children, props) {
  const rows = useMemo(() => {
    const result = [];
    React.Children.forEach(children, (child) => {
      if (child && child.type === SplitPanelRow) {
        result.push({
          key: child.props.row,
          size: child.props.height,
        });
      }
    });
    return result;
  }, [children]);
  const childrenArray = React.Children.map(children, (child, index) => {
    if (child && child.type === SplitPanelRow) {
      return React.cloneElement(
        child,
        {
          ...props,
          row: props.row || index,
        },
      );
    }
    return null;
  });
  return {
    rows,
    children: childrenArray,
  };
}

function SplitPanelGrid(
  {
    className,
    style,
    resizerClassName,
    resizerStyle,
    panelClassName,
    panelStyle,
    children,
    resizable,
    ...props
  },
) {
  const commonProps = useMemo(() => ({
    resizable,
    panelClassName,
    panelStyle,
    resizerStyle,
    resizerClassName,
  }), [
    resizable,
    panelClassName,
    panelStyle,
    resizerStyle,
    resizerClassName,
  ]);
  const {
    children: childrenArray,
    rows,
  } = useRows(children, commonProps);
  const gridStyle = useGridStyle(rows);
  const mergedStyle = useMemo(
    () => ({ ...(style || []), ...gridStyle }),
    [style, gridStyle],
  );
  return (
    <div
      className={
        classNames(
          className,
          'split-panel-grid',
        )
      }
      style={mergedStyle}
      {...props}
    >
      {childrenArray}
    </div>
  );
}

SplitPanelGrid.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  resizerClassName: PropTypes.string,
  resizerStyle: PropTypes.object,
  panelClassName: PropTypes.string,
  panelStyle: PropTypes.object,
  children: PropTypes.node.isRequired,
  resizable: PropTypes.bool,
};

SplitPanelGrid.defaultProps = {
  className: undefined,
  style: undefined,
  resizerClassName: undefined,
  resizerStyle: undefined,
  panelClassName: undefined,
  panelStyle: undefined,
  resizable: true,
};

export default SplitPanelGrid;
