import React from 'react';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import styles from './scheme-parameter-input.css';
import {renderParameter} from '../renderers';
import {checkEntryPropertyValid} from "./utilities";

function LaunchFormSchemeParameterEntryProp (props) {
  const {
    className,
    style,
    disabled,
    entry,
    property,
    onChange
  } = props;
  const entryProperty = entry[property.name] || {};
  const {
    value
  } = entryProperty;
  const valid = checkEntryPropertyValid(entry, property);
  const param = property ? {
    ...property,
    config: property,
    valid
  } : undefined;
  const onChangePropValue = (o) => {
    const {value: newValue} = o || {};
    const result = {
      ...entry,
      [property.name]: {
        value: newValue,
        type: property.type
      }
    };
    if (onChange) {
      onChange(result);
    }
  };
  const parameterComponent = param ? renderParameter({
    className: classNames({'cp-error': !valid}),
    disabled,
    parameter: param,
    onChange: onChangePropValue,
    value,
    style: {
      width: '100%'
    }
  }) : undefined;
  if (!parameterComponent) {
    return (
      <td
        key={property.name}
        className={
          classNames(className, styles.parameterCol)
        }
        style={style}
      >
        {'\u00A0'}
      </td>
    );
  }
  return (
    <td
      key={property.name}
      className={
        classNames(
          className,
          'cp-divider left right',
          styles.parameterCol,
          styles[`parameter-type-${property.type}`]
        )
      }
      style={style}
    >
      {parameterComponent}
    </td>
  );
}

LaunchFormSchemeParameterEntryProp.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  property: PropTypes.object,
  entry: PropTypes.any,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormSchemeParameterEntryProp;
