import React from 'react';
import {isObservableArray} from 'mobx';

const parameterRenderers = {};
const defaultRendererKey = '__default__';

export function registerRenderer(type, renderer, defaultRenderer = false) {
  parameterRenderers[type.toLowerCase()] = renderer;
  if (defaultRenderer) {
    parameterRenderers[defaultRendererKey] = renderer;
  }
}

function getRenderer(type) {
  return (
    (type ? parameterRenderers[type.toLowerCase()] : undefined) ||
    parameterRenderers[defaultRendererKey]
  );
}

/**
 * @typedef {Object} RenderParameterOptions
 * @property {string} [className]
 * @property {object} [style]
 * @property {Parameter} [parameter]
 * @property {func} onChange
 * @property {boolean} [disabled]
 * @property {boolean} [rawEdit]
 * @property currentProjectId
 * @property currentProjectMetadata
 * @property currentMetadataEntity
 * @property rootEntityId
 * @property metadataAutoComplete
 */
/**
 * @param {RenderParameterOptions} props
 */
export function renderParameter(props) {
  const {parameter, onChange, disabled, rawEdit, ...rest} = props;
  if (!parameter || typeof parameter !== 'object') {
    return null;
  }
  let {type = 'string', value, config = {}} = parameter;
  const {readOnly: readOnlyValue = false, required = false, enumeration} = config;
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
        value: newValue,
      });
    }
  };
  const parameterProps = {
    value,
    parameter,
    onChange: onParameterValueChange,
    disabled: readOnly || disabled,
    required,
    ...rest,
  };
  const Renderer = getRenderer(type);
  if (Renderer) {
    return <Renderer {...parameterProps} />;
  }
  return null;
}
