import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Checkbox} from 'antd';
import styles from './launch-form-parameter-input.module.css';

function LaunchFormBoolParameterInput(props) {
  const {className, style, value, onChange, disabled} = props;
  const checked = value ? String(value).toLowerCase() === 'true' : false;
  const onCheckboxValueChange = (e) => {
    if (typeof onChange === 'function') {
      onChange(e.target.checked);
    }
  };
  return (
    <Checkbox
      className={classNames(
        className,
        styles.launchParameterInput,
        styles.launchParameterBoolInput,
      )}
      style={style}
      checked={checked}
      onChange={onCheckboxValueChange}
      disabled={disabled}
    >
      Enabled
    </Checkbox>
  );
}

LaunchFormBoolParameterInput.propTypes = {
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
  metadataAutoComplete: PropTypes.bool,
};

export default LaunchFormBoolParameterInput;
