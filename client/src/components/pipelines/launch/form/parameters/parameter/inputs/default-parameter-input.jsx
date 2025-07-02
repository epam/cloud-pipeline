import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './launch-form-parameter-input.css';
import MetadataAutoComplete from './metadata-auto-complete';

function LaunchFormStringParameterInput (props) {
  const {
    className,
    style,
    value,
    onChange,
    disabled,
    currentProjectId,
    currentMetadataEntity,
    currentProjectMetadata,
    rootEntityId,
    metadataAutoComplete
  } = props;
  return (
    <MetadataAutoComplete
      className={classNames(className, styles.launchParameterInput)}
      style={style}
      value={value ? String(value) : ''}
      onChange={onChange}
      disabled={disabled}
      size="large"
      currentProjectId={currentProjectId}
      currentMetadataEntity={currentMetadataEntity}
      currentProjectMetadata={currentProjectMetadata}
      rootEntityId={rootEntityId}
      defaultInput={!metadataAutoComplete}
    />
  );
}

LaunchFormStringParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormStringParameterInput;
