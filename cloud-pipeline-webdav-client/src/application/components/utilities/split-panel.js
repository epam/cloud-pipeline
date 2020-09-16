import React, {
  useEffect,
  useCallback,
  useState,
  useLayoutEffect,
  useRef
} from 'react';
import classNames from 'classnames';
import './split-panel.css';

const PANEL_PADDING = 2;
const RESIZER_SIZE = 2;
const MINIMUM_SIZE = 50;

function extractPercent (value) {
  const percent = /^(.+)%$/.exec(value);
  if (percent && percent.length > 1 && !Number.isNaN(Number(percent[1]))) {
    return Number(percent[1]);
  }
  return 0;
}

function getPercentValue (value, totalWidth) {
  if (!value) {
    return undefined;
  }
  if (!Number.isNaN(Number(value))) {
    // pixels
    return `${Number(value) / totalWidth * 100}%`;
  }
  const percent = /^(.+)%$/.exec(value);
  if (percent && percent.length > 1 && !Number.isNaN(Number(percent[1]))) {
    return value;
  }
  return undefined;
}

function useSplitPanel(sizes) {
  return useState(sizes);
}

function sizesDescription (sizes) {
  return (sizes || []).map(s => `${s}`).join('|');
}

function SplitPanel(
  {
    children,
    className,
    style,
    sizes: panelSizes,
    onChange: setPanelSizes,
    resizer: resizerSize = RESIZER_SIZE,
    resizerStyle,
    main = true,
    fadeOnResize = false,
  },
) {
  const childrenArray = Array.isArray(children) ? children : [children];
  const containerRef = useRef(null);
  const [resizer, setResizer] = useState(null);
  useLayoutEffect(() => {
    if (
      !resizer &&
      main &&
      panelSizes &&
      panelSizes.length &&
      containerRef.current &&
      containerRef.current.clientWidth
    ) {
      const totalWidth = containerRef.current.clientWidth -
        panelSizes.length * 2 * PANEL_PADDING;
      const newSizes = panelSizes.map(size => getPercentValue(size, totalWidth));
      const used = newSizes.map(extractPercent).reduce((r, c) => r + c, 0);
      const left = Math.max(0, 100.0 - used);
      const perPanel = left / newSizes.filter(s => !s).length;
      const sizes = newSizes.map(s => s || `${perPanel}%`);
      if (sizesDescription(sizes) !== sizesDescription(panelSizes)) {
        setPanelSizes(sizes);
      }
    }
  }, [
    containerRef.current,
    containerRef.current?.clientWidth,
    panelSizes,
    setPanelSizes,
    resizer,
    main
  ]);
  const getPosition = useCallback((e) => {
    if (!containerRef.current) {
      return 0;
    }
    const rect = containerRef.current.getBoundingClientRect();
    return e.clientX - rect.left;
  }, [containerRef]);
  const onMouseDown = useCallback((index, e) => {
    if (containerRef.current) {
      setResizer({
        index,
        position: getPosition(e),
        sizes: panelSizes.slice(),
        tempSizes: panelSizes.slice()
      });
      e.stopPropagation();
      e.preventDefault();
      return false;
    }
    return true;
  }, [setPanelSizes, containerRef, setResizer, getPosition, panelSizes]);
  const onMouseMove = useCallback((e) => {
    if (resizer && containerRef.current) {
      const totalWidth = (
        containerRef.current.clientWidth -
        resizer.sizes.length * 2 * PANEL_PADDING
      );
      const sizes = resizer.sizes
        .map(extractPercent)
        .map(size => size * totalWidth / 100.0);
      let delta = getPosition(e) - resizer.position;
      if (delta < 0) {
        delta = -Math.min(sizes[resizer.index] - MINIMUM_SIZE, Math.abs(delta));
      } else {
        delta = Math.min(sizes[resizer.index + 1] - MINIMUM_SIZE, Math.abs(delta));
      }
      sizes[resizer.index] = sizes[resizer.index] + delta;
      sizes[resizer.index + 1] = sizes[resizer.index + 1] - delta;
      e.stopPropagation();
      e.preventDefault();
      setResizer({
        ...resizer,
        tempSizes: sizes.map((size) => getPercentValue(size, totalWidth))
      });
      return false;
    }
    return true;
  }, [resizer, getPosition, containerRef, setPanelSizes]);
  const onMouseUp = useCallback((e) => {
    onMouseMove(e);
    if (resizer) {
      setPanelSizes(resizer.tempSizes);
      setResizer(null);
      e.stopPropagation();
      e.preventDefault();
      return false;
    }
    return true;
  }, [resizer, setResizer]);
  useEffect(() => {
    document.body.addEventListener('mousemove', onMouseMove);
    document.body.addEventListener('mouseup', onMouseUp);
    return () => {
      document.body.removeEventListener('mousemove', onMouseMove);
      document.body.removeEventListener('mouseup', onMouseUp);
    };
  }, [onMouseMove, onMouseUp]);
  const widths = resizer ? resizer.tempSizes : panelSizes;
  const panels = childrenArray
    .map((child, index, array) => (
      <div
        key={`child-${index}`}
        className={
          classNames(
            'panel',
            {
              resizing: resizer && fadeOnResize
                ? [resizer.index, resizer.index + 1].indexOf(index) >= 0
                : false
            }
          )
        }
        style={{
          width: widths && widths[index] ? widths[index] : 0,
          padding: PANEL_PADDING
        }}
      >
        {child}
        <div className="split-panel-overlay">
          {'\u00A0'}
        </div>
        {
          (index < array.length - 1) && (
            <div
              className="resizer"
              onMouseDown={e => onMouseDown(index, e)}
              style={{
                width: resizerSize + 4,
                right: -(resizerSize / 2.0 + 2)
              }}
            >
              <div
                className="border"
                style={
                  Object.assign(
                    {},
                    resizerStyle,
                    {
                      width: 2,
                      borderWidth: resizerSize
                    })
                }
              >
                {'\u00A0'}
              </div>
            </div>
          )
        }
      </div>
    ));
  return (
    <div
      ref={containerRef}
      className={classNames('split-panel', className)}
      style={style}
    >
      {panels}
    </div>
  );
}

export {useSplitPanel, RESIZER_SIZE, PANEL_PADDING};
export default SplitPanel;
