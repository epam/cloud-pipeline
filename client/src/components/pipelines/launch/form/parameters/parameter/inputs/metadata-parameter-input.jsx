import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import MetadataParameterInput from '../../../MetadataParameterInput';
import styles from './launch-form-parameter-input.css';

function LaunchFormMetadataParameterInput (props) {
  const {
    className,
    style,
    value,
    onChange,
    disabled,
    currentProjectId,
    currentMetadataEntity = [],
    rootEntityId
  } = props;
  return (
    <MetadataParameterInput
      className={classNames(className, styles.launchParameterInput)}
      style={style}
      disabled={disabled}
      onSelectMetadata={onChange}
      currentProjectId={currentProjectId}
      rootEntityId={rootEntityId}
      currentMetadataEntity={currentMetadataEntity.slice()}
      value={value}
    />
  );
}

LaunchFormMetadataParameterInput.propTypes = {
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

export default LaunchFormMetadataParameterInput;
