import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Form, Icon} from 'antd';
import LaunchFormParameterInput from './inputs';
import styles from './launch-form-parameter.css';
import ParameterNameInput from './name-input';
import {getParameterKeyClassName} from '../utilities';

function LaunchFormParameter (props) {
  const {
    className,
    style,
    parameter,
    onChange,
    disabled,
    onRemoveParameter,
    rawEdit,
    editConfiguration,
    currentCloudRegionId,
    currentProjectId,
    currentProjectMetadata,
    currentMetadataEntity,
    rootEntityId,
    metadataAutoComplete,
    detached = false,
    pipeline = false
  } = props;
  if (!parameter) {
    return null;
  }
  const {name, config = {}, error, warning, system} = parameter;
  const {
    description,
    required = false,
    readOnly = false
  } = config;
  const removeAllowed = !readOnly &&
    !disabled &&
    typeof onRemoveParameter === 'function' &&
    (system || rawEdit || (!required && !(detached && pipeline)));
  const onRemoveParameterClicked = () => {
    if (typeof onRemoveParameter === 'function' && removeAllowed && !disabled) {
      onRemoveParameter(parameter);
    }
  };
  return (
    <div
      className={classNames(
        className,
        'ant-row', // for tests compatability
        'ant-form-item', // for tests compatability
        styles.launchFormParameter,
        name ? `launch-form-parameter-${name}` : undefined,
        getParameterKeyClassName(parameter)
      )}
      style={style}
    >
      <ParameterNameInput
        disabled={disabled || (detached && pipeline)}
        rawEdit={rawEdit}
        parameter={parameter}
        onChange={onChange}
        style={{paddingRight: 30}}
        editConfiguration={editConfiguration}
      />
      <div style={{display: 'flex', flexWrap: 'nowrap', fontSize: 'larger'}}>
        <Form.Item
          validateStatus={error ? 'error' : warning ? 'warning' : 'success'}
          hasFeedback
          style={{flex: 1, marginBottom: 0}}>
          <LaunchFormParameterInput
            style={{width: '100%', minHeight: 32}}
            parameter={parameter}
            onChange={onChange}
            disabled={disabled}
            rawEdit={rawEdit}
            currentCloudRegionId={currentCloudRegionId}
            currentProjectId={currentProjectId}
            currentProjectMetadata={currentProjectMetadata}
            currentMetadataEntity={currentMetadataEntity}
            rootEntityId={rootEntityId}
            metadataAutoComplete={metadataAutoComplete}
          />
        </Form.Item>
        {removeAllowed && typeof onRemoveParameter === 'function' ? (
          <Icon
            className="dynamic-delete-button"
            type="minus-circle-o"
            onClick={onRemoveParameterClicked}
            style={{
              verticalAlign: 'middle',
              marginTop: '2px',
              fontSize: 'larger',
              cursor: 'pointer',
              alignSelf: 'flex-start',
              margin: 'auto -2px auto 15px',
              height: '100%'
            }}
          />
        ) : (
          <div
            style={{
              marginLeft: 15,
              width: 15,
              display: 'inline-block'
            }}>{'\u00A0'}</div>
        )}
      </div>
      {
        error && (
          <div className="cp-error" style={{margin: 0}}>
            {error}
          </div>
        )
      }
      {
        !error && warning && (
          <div className="cp-warning" style={{margin: 0}}>
            {warning}
          </div>
        )
      }
      {
        description && (
          <div className="cp-text-not-important">
            {description}
          </div>
        )
      }
    </div>
  );
}

LaunchFormParameter.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  disabled: PropTypes.bool,
  onRemoveParameter: PropTypes.func,
  editConfiguration: PropTypes.bool,
  rawEdit: PropTypes.bool,
  currentCloudRegionId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool,
  detached: PropTypes.bool,
  pipeline: PropTypes.bool
};

export default LaunchFormParameter;
