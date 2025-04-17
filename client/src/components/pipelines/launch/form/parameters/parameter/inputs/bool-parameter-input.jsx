import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Checkbox} from 'antd';
import styles from './launch-form-parameter-input.css';

function LaunchFormBoolParameterInput (props) {
  const {
    className,
    style,
    value,
    onChange,
    readOnly,
    disabled
  } = props;
  const checked = value ? String(value).toLowerCase() === 'true' : false;
  const onCheckboxValueChange = (e) => {
    if (typeof onChange === 'function') {
      onChange(e.target.checked);
    }
  };
  return (
    <Checkbox
      className={classNames(className, styles.launchParameterInput)}
      style={style}
      checked={checked}
      onChange={onCheckboxValueChange}
      disabled={readOnly || disabled}
    />
  );
}

LaunchFormBoolParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  disabled: PropTypes.bool
};

export default LaunchFormBoolParameterInput;
