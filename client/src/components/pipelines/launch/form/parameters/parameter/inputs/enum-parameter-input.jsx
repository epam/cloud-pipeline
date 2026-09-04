import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Select} from 'antd';
import styles from './launch-form-parameter-input.css';

function unMapValue (value, parameter) {
  if (parameter?.config?.multiple && Array.isArray(value)) {
    return value.join(',');
  }
  return value;
}

function mapValue (value = '', parameter) {
  if (parameter?.config?.multiple) {
    return (value || '').split(',').filter(Boolean);
  }
  return String(value);
}

function LaunchFormEnumParameterInput (props) {
  const {
    className,
    style,
    value: valueProps,
    parameter,
    onChange,
    disabled
  } = props;
  const {
    config = {}
  } = parameter || {};
  const {
    enumeration = []
  } = config;
  const visibleEnumeration = enumeration.filter((o) => o.visible);
  const value = mapValue(valueProps, parameter);
  const onInputChange = (e) => {
    if (typeof onChange === 'function') {
      onChange(unMapValue(e, parameter));
    }
  };
  return (
    <Select
      className={classNames(className, styles.launchParameterInput)}
      style={style}
      value={value}
      onChange={onInputChange}
      disabled={disabled}
      size="large"
      mode={parameter?.config?.multiple ? 'multiple' : 'default'}
      optionLabelProp="label"
    >
      {visibleEnumeration.map((v) => (
        <Select.Option key={v.value} value={v.value} label={v.name || v.value}>
          {v.name || v.value}
        </Select.Option>
      ))}
    </Select>
  );
}

LaunchFormEnumParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  parameter: PropTypes.object,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormEnumParameterInput;
