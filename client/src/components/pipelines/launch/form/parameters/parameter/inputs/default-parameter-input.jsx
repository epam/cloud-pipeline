import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Input} from 'antd';
import styles from './launch-form-parameter-input.css';

function LaunchFormStringParameterInput (props) {
  const {
    className,
    style,
    value,
    onChange,
    readOnly,
    disabled
  } = props;
  const onInputChange = (e) => {
    if (typeof onChange === 'function') {
      onChange(e.target.value || '');
    }
  };
  return (
    <Input
      className={classNames(className, styles.launchParameterInput)}
      style={style}
      value={value ? String(value) : ''}
      onChange={onInputChange}
      disabled={readOnly || disabled}
      size="large"
    />
  );
}

LaunchFormStringParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  disabled: PropTypes.bool
};

export default LaunchFormStringParameterInput;
