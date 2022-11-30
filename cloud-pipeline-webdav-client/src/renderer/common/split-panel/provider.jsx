import React, {
  useEffect,
  useMemo,
} from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import './split-panel.css';
import {
  SplitPanelContext,
  useCreateSplitPanelContext,
} from './context';

function SplitPanelProvider(
  {
    className,
    style,
    panelsCount,
    sizes,
    columns,
    onChange,
    resizer,
    children,
  },
) {
  const options = useMemo(() => ({
    defaultSizes: sizes,
    resizerSize: resizer,
    columns,
  }), [sizes, resizer, columns]);
  const state = useCreateSplitPanelContext(panelsCount, options);
  const {
    sizes: panelSizes,
    container: containerRef,
  } = state;
  useEffect(() => {
    if (typeof onChange === 'function') {
      onChange(panelSizes);
    }
  }, [onChange, panelSizes]);
  return (
    <SplitPanelContext.Provider value={state}>
      <div
        ref={containerRef}
        className={
          classNames(
            className,
            'split-panel',
          )
        }
        style={style}
      >
        {children}
      </div>
    </SplitPanelContext.Provider>
  );
}

SplitPanelProvider.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node.isRequired,
  sizes: PropTypes.oneOfType([
    PropTypes.object,
    PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.string, PropTypes.number])),
  ]),
  columns: PropTypes.arrayOf(PropTypes.string),
  onChange: PropTypes.func,
  resizer: PropTypes.number,
  panelsCount: PropTypes.number.isRequired,
};

SplitPanelProvider.defaultProps = {
  className: undefined,
  style: undefined,
  resizer: 1,
  sizes: undefined,
  onChange: undefined,
  columns: undefined,
};

export default SplitPanelProvider;
