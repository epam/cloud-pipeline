import React from 'react';
import PropTypes from 'prop-types';
import LaunchFormStringParameterInput from './default-parameter-input';
import LaunchFormBoolParameterInput from './bool-parameter-input';
import LaunchFormPathParameterInput from './path-parameter-input';
import {isObservableArray} from 'mobx';
import LaunchFormEnumParameterInput from './enum-parameter-input';
import LaunchFormMetadataParameterInput from './metadata-parameter-input';
import LaunchFormSchemeParameterInput from './scheme-parameter-input/scheme-parameter-input';
import LaunchFormMetadataEntityParameter from './metadata-entity-parameter';

function DefaultInputSelector (props) {
  const {
    className,
    style,
    parameter,
    onChange,
    disabled,
    rawEdit,
    currentProjectId,
    currentProjectMetadata,
    currentMetadataEntity,
    rootEntityId,
    metadataAutoComplete
  } = props;
  if (!parameter || typeof parameter !== 'object') {
    return null;
  }
  let {
    type = 'string',
    value,
    config = {}
  } = parameter;
  const {
    readOnly: readOnlyValue = false,
    required = false,
    enumeration
  } = config;
  const readOnly = rawEdit ? false : readOnlyValue;
  if (typeof type !== 'string') {
    type = 'string';
  }
  if (
    enumeration &&
    typeof enumeration === 'object' &&
    (Array.isArray(enumeration) || isObservableArray(enumeration)) &&
    enumeration.length > 0 &&
    !rawEdit
  ) {
    type = 'enum';
  }
  const onParameterValueChange = (newValue) => {
    if (typeof onChange === 'function') {
      onChange({
        ...parameter,
        value: newValue
      });
    }
  };
  switch (type.toLowerCase()) {
    case 'scheme':
      return (
        <LaunchFormSchemeParameterInput
          className={className}
          style={style}
          parameter={parameter}
          value={value}
          onChange={onParameterValueChange}
          disabled={readOnly || disabled}
          required={required}
          currentProjectId={currentProjectId}
          currentProjectMetadata={currentProjectMetadata}
          currentMetadataEntity={currentMetadataEntity}
          rootEntityId={rootEntityId}
          metadataAutoComplete={metadataAutoComplete}
        />
      );
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
          disabled={readOnly || disabled}
          required={required}
          currentProjectId={currentProjectId}
          currentProjectMetadata={currentProjectMetadata}
          currentMetadataEntity={currentMetadataEntity}
          rootEntityId={rootEntityId}
          metadataAutoComplete={metadataAutoComplete}
        />
      );
    case 'boolean':
      return (
        <LaunchFormBoolParameterInput
          className={className}
          style={style}
          value={value}
          onChange={onParameterValueChange}
          disabled={readOnly || disabled}
          required={required}
          currentProjectId={currentProjectId}
          currentProjectMetadata={currentProjectMetadata}
          currentMetadataEntity={currentMetadataEntity}
          rootEntityId={rootEntityId}
          metadataAutoComplete={metadataAutoComplete}
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
          disabled={readOnly || disabled}
          required={required}
          enumeration={enumeration}
          currentProjectId={currentProjectId}
          currentProjectMetadata={currentProjectMetadata}
          currentMetadataEntity={currentMetadataEntity}
          rootEntityId={rootEntityId}
          metadataAutoComplete={metadataAutoComplete}
        />
      );
    case 'metadata':
      return (
        <LaunchFormMetadataParameterInput
          className={className}
          style={style}
          value={value}
          onChange={onParameterValueChange}
          disabled={readOnly || disabled}
          required={required}
          currentProjectId={currentProjectId}
          currentProjectMetadata={currentProjectMetadata}
          currentMetadataEntity={currentMetadataEntity}
          rootEntityId={rootEntityId}
          metadataAutoComplete={metadataAutoComplete}
        />
      );
    case 'metadata_entity':
      return (
        <LaunchFormMetadataEntityParameter
          className={className}
          style={style}
          value={value}
          onChange={onParameterValueChange}
          disabled={readOnly || disabled}
          required={required}
          currentProjectId={currentProjectId}
          currentProjectMetadata={currentProjectMetadata}
          currentMetadataEntity={currentMetadataEntity}
          rootEntityId={rootEntityId}
          metadataAutoComplete={metadataAutoComplete}
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
          disabled={readOnly || disabled}
          required={required}
          currentProjectId={currentProjectId}
          currentProjectMetadata={currentProjectMetadata}
          currentMetadataEntity={currentMetadataEntity}
          rootEntityId={rootEntityId}
          metadataAutoComplete={metadataAutoComplete}
        />
      );
  }
}

DefaultInputSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  rawEdit: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default DefaultInputSelector;
