/* eslint-disable react/jsx-props-no-spreading */
import React, { useMemo } from 'react';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import { useColumnKey, useColumnResizerKey, useColumnsSpan } from './context';
import SplitPanelResizer from './resizer';
import { getGridIndex } from './utilities';
import { useSplitPanelRow } from './row-context';

function useColumnKeys(column, spread = false) {
  return useMemo(() => {
    if (spread) {
      return [0, -1];
    }
    if (column && Array.isArray(column)) {
      return column;
    }
    return [column, column];
  }, [column, spread]);
}

function SplitPanelColumn(
  {
    column: columnNameOrIndex,
    spread,
    resizerClassName,
    resizerStyle,
    className,
    style,
    children,
    resizable,
    ...props
  },
) {
  const row = useSplitPanelRow();
  const [start, end] = useColumnKeys(columnNameOrIndex, spread);
  const columnStart = useColumnKey(start);
  const columnEnd = useColumnKey(end);
  const span = useColumnsSpan(columnStart, columnEnd);
  const gridRow = useMemo(() => getGridIndex(row), [row]);
  const columnResizerKey = useColumnResizerKey(columnEnd);
  const panelStyleComputed = useMemo(() => ({
    ...(style || {}),
    gridColumnStart: columnStart,
    gridColumnEnd: `span ${span}`,
    gridRow,
  }), [style, gridRow, columnStart, span]);
  return (
    <>
      <div
        {...props}
        className={
          classNames(
            className,
            'panel',
          )
        }
        key={columnStart}
        style={panelStyleComputed}
        data-panel={resizable ? columnEnd : undefined}
      >
        {children}
      </div>
      {
        columnResizerKey && (
          <SplitPanelResizer
            column={columnEnd}
            row={row}
            className={resizerClassName}
            style={resizerStyle}
            resizable={resizable}
          />
        )
      }
    </>
  );
}

const ColumnPropType = PropTypes.oneOfType([PropTypes.string, PropTypes.number]);

SplitPanelColumn.propTypes = {
  column: PropTypes.oneOfType([ColumnPropType, PropTypes.arrayOf(ColumnPropType)]),
  spread: PropTypes.bool,
  resizerClassName: PropTypes.string,
  resizerStyle: PropTypes.object,
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node.isRequired,
  resizable: PropTypes.bool,
};

SplitPanelColumn.defaultProps = {
  column: undefined,
  spread: false,
  resizerClassName: undefined,
  resizerStyle: undefined,
  className: undefined,
  style: undefined,
  resizable: true,
};

export default SplitPanelColumn;
