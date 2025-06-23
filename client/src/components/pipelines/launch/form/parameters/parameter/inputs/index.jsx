import React from 'react';
import PropTypes from 'prop-types';
import LaunchFormStringParameterInput from './default-parameter-input';
import LaunchFormBoolParameterInput from './bool-parameter-input';
import LaunchFormPathParameterInput from './path-parameter-input';
import {isObservableArray} from 'mobx';
import LaunchFormEnumParameterInput from './enum-parameter-input';

function LaunchFormParameterInput (props) {
  const {
    className,
    style,
    parameter,
    onChange,
    disabled
  } = props;
  if (!parameter || typeof parameter !== 'object') {
    return null;
  }
  let {
    type = 'string',
    value,
    no_override: _noOverride = false,
    noOverride = _noOverride,
    read_only: _readOnly = false,
    readonly = _readOnly,
    readOnly = readonly,
    required = false,
    enum: _enum,
    enumeration = _enum
  } = parameter;
  if (typeof type !== 'string') {
    type = 'string';
  }
  if (
    enumeration &&
    typeof enumeration === 'object' &&
    (Array.isArray(enumeration) || isObservableArray(enumeration)) &&
    enumeration.length > 0
  ) {
    type = 'enum';
  }
  const onParameterValueChange = (newValue) => {
    if (typeof onChange === 'function' && !readOnly && !disabled && !noOverride) {
      onChange({
        ...parameter,
        value: newValue
      });
    }
  };
  switch (type.toLowerCase()) {
    case 'path':
    case 'output':
    case 'input':
    case 'common':
      return (
        <LaunchFormPathParameterInput
          className={className}
          style={style}
          value={value}
          pathType={type.toLowerCase()}
          onChange={onParameterValueChange}
          readOnly={readOnly || noOverride}
          disabled={readOnly || noOverride || disabled}
          required={required}
        />
      );
    case 'boolean':
      return (
        <LaunchFormBoolParameterInput
          className={className}
          style={style}
          value={value}
          onChange={onParameterValueChange}
          readOnly={readOnly || noOverride}
          disabled={readOnly || noOverride || disabled}
          required={required}
        />
      );
    case 'enum':
    case 'enumeration':
      return (
        <LaunchFormEnumParameterInput
          className={className}
          style={style}
          value={value}
          onChange={onParameterValueChange}
          readOnly={readOnly || noOverride}
          disabled={readOnly || noOverride || disabled}
          required={required}
          enumeration={enumeration}
        />
      );
    case 'string':
    default:
      return (
        <LaunchFormStringParameterInput
          className={className}
          style={style}
          value={value}
          onChange={onParameterValueChange}
          readOnly={readOnly || noOverride}
          disabled={readOnly || noOverride || disabled}
          required={required}
        />
      );
  }
}

LaunchFormParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  onChange: PropTypes.func,
  disabled: PropTypes.bool
};

export default LaunchFormParameterInput;
