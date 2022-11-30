import React from 'react';
import PropTypes from 'prop-types';
import types from '../types';
import { usePlatformSpecificHotKey } from '../hooks/use-platform-specific-hotkeys';

const names = {
  [types.createDirectory]: 'Create directory',
  [types.copy]: 'Copy',
  [types.move]: 'Move',
  [types.refresh]: 'Reload',
  [types.remove]: 'Delete',
};

function OperationName(
  {
    className,
    style,
    type,
  },
) {
  const hotKey = usePlatformSpecificHotKey(type);
  return (
    <span
      className={className}
      style={style}
    >
      {names[type]}
      {
        hotKey ? ` (${hotKey.name})` : undefined
      }
    </span>
  );
}

OperationName.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  type: PropTypes.oneOf(Object.values(types)).isRequired,
};

OperationName.defaultProps = {
  className: undefined,
  style: undefined,
};

export default OperationName;
