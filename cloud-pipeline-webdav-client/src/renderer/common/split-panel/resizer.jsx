import React, {
  useEffect,
  useMemo,
  useRef,
  useCallback,
} from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import './split-panel.css';
import {
  useColumnResizerKey,
  useResizerAffectedColumnsKeys,
  useResizerChangeSizesCallback,
  useResizerCommitSizesCallback,
} from './context';
import {
  onMouseDownCallback,
  onMouseMoveCallback,
  onMouseUpCallback,
} from './mouse-events';
import { getGridIndex } from './utilities';

function SplitPanelResizer(
  {
    className,
    style,
    column,
    row,
    resizable,
  },
) {
  const columnResizerKey = useColumnResizerKey(column);
  const [left, right] = useResizerAffectedColumnsKeys(column);
  const resizerRef = useRef(undefined);
  const resizeEvent = useRef(undefined);
  const changeSizes = useResizerChangeSizesCallback(column);
  const commitSizes = useResizerCommitSizesCallback(column);
  const onMouseDown = useCallback((event) => {
    if (
      resizable
      && resizerRef.current
      && resizerRef.current.parentNode
    ) {
      onMouseDownCallback(
        event,
        resizeEvent,
        {
          dataPanelRight: right,
          dataPanelLeft: left,
          resizerContainer: resizerRef.current.parentNode,
        },
      );
    }
  }, [
    resizable,
    left,
    right,
  ]);
  const onMouseMove = useCallback(
    (event) => onMouseMoveCallback(event, resizeEvent, changeSizes),
    [resizeEvent, changeSizes],
  );
  const onMouseUp = useCallback(
    (event) => onMouseUpCallback(event, resizeEvent, commitSizes),
    [resizeEvent, commitSizes],
  );
  useEffect(() => {
    onMouseUp();
    if (resizable) {
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
      document.addEventListener('visibilitychange', onMouseUp);
      return () => {
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
        document.removeEventListener('visibilitychange', onMouseUp);
      };
    }
    return () => {};
  }, [resizable, onMouseMove, onMouseUp]);
  const resizerStyle = useMemo(() => ({
    ...(style || {}),
    gridColumn: columnResizerKey,
    gridRow: getGridIndex(row),
  }), [columnResizerKey, style, row]);
  return (
    // eslint-disable-next-line jsx-a11y/interactive-supports-focus
    <div
      key={columnResizerKey}
      ref={resizerRef}
      style={resizerStyle}
      className={
        classNames(
          className,
          'resizer',
          {
            resizable,
          },
        )
      }
      role="tab"
      onMouseDown={onMouseDown}
    >
      {'\u00A0'}
    </div>
  );
}

SplitPanelResizer.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  column: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  row: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  resizable: PropTypes.bool,
};

SplitPanelResizer.defaultProps = {
  className: undefined,
  style: undefined,
  resizable: true,
};

export default SplitPanelResizer;
