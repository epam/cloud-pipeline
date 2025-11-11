import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import { Checkbox } from 'antd';
import classNames from 'classnames';
import './configuration.css';

function BooleanProperty(
  {
    className,
    style,
    property,
    value,
    onChange,
    disabled,
  },
) {
  const onChangeCallback = useCallback((event) => {
    if (typeof onChange === 'function') {
      onChange(event.target.checked);
    }
  }, [onChange]);
  return (
    <div
      className={
        classNames(
          className,
          'configuration-row',
        )
      }
      style={style}
    >
      <Checkbox
        checked={value}
        onChange={onChangeCallback}
        disabled={disabled}
      >
        {property}
      </Checkbox>
    </div>
  );
}

BooleanProperty.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  property: PropTypes.string.isRequired,
  value: PropTypes.bool,
  onChange: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
};

BooleanProperty.defaultProps = {
  className: undefined,
  style: undefined,
  value: undefined,
  disabled: false,
};

export default BooleanProperty;
