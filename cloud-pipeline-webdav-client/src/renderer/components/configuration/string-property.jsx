import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import { Input } from 'antd';
import classNames from 'classnames';
import './configuration.css';

function StringProperty(
  {
    className,
    style,
    property,
    propertyStyle,
    secure,
    value,
    onChange,
    disabled,
    children,
    suffix,
    placeholder,
  },
) {
  const onChangeCallback = useCallback((event) => {
    if (typeof onChange === 'function') {
      onChange(event.target.value);
    }
  }, [onChange]);
  let InputComponent = Input;
  if (secure) {
    InputComponent = Input.Password;
  }
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
      <div
        className="property-title-container"
        style={propertyStyle}
      >
        {property}
        :
      </div>
      <InputComponent
        className="property-value-container"
        value={value}
        onChange={onChangeCallback}
        disabled={disabled}
        suffix={suffix}
        placeholder={placeholder}
      />
      {children}
    </div>
  );
}

StringProperty.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  property: PropTypes.string.isRequired,
  propertyStyle: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  secure: PropTypes.bool,
  children: PropTypes.node,
  suffix: PropTypes.node,
  placeholder: PropTypes.string,
};

StringProperty.defaultProps = {
  className: undefined,
  style: undefined,
  secure: false,
  value: undefined,
  disabled: false,
  children: undefined,
  suffix: undefined,
  onChange: undefined,
  propertyStyle: undefined,
  placeholder: undefined,
};

export default StringProperty;
