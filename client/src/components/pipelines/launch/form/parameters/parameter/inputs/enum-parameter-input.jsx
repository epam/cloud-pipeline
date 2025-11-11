import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Select} from 'antd';
import styles from './launch-form-parameter-input.css';

function mapEnumerationItem (eItem) {
  if (typeof eItem === 'string') {
    return {
      key: eItem,
      value: eItem,
      visible: () => true
    };
  }
  if (typeof eItem === 'number') {
    return {
      key: `${eItem}`,
      value: `${eItem}`,
      visible: () => true
    };
  }
  if (typeof eItem === 'object') {
    const {value, visible} = eItem;
    return {
      key: value,
      value,
      visible: () => visible
    };
  }
  return undefined;
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
    enumeration: enumerationProps = []
  } = config;
  const enumeration = (enumerationProps || []).map(mapEnumerationItem);
  const value = valueProps ? String(valueProps) : undefined;
  const onInputChange = (e) => {
    if (typeof onChange === 'function') {
      onChange(e);
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
    >
      {enumeration.map((v) => (
        <Select.Option key={v.key} value={v.value}>
          {v.value}
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
