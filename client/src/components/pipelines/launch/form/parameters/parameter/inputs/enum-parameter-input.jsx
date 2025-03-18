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
    onChange,
    readOnly,
    disabled,
    enumeration: enumerationProps = []
  } = props;
  const enumeration = enumerationProps.map(mapEnumerationItem);
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
      disabled={readOnly || disabled}
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
  enumeration: PropTypes.any,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  disabled: PropTypes.bool
};

export default LaunchFormEnumParameterInput;
