import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Form} from 'antd';
import {MinusCircleOutlined} from '@ant-design/icons';
import LaunchFormParameterInput from './inputs';
import styles from './launch-form-parameter.module.css';
import ParameterNameInput from './name-input';
import {getParameterKeyClassName} from '../utilities';
import Markdown from '../../../../../special/markdown';

function LaunchFormParameter(props) {
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
    pipeline = false,
    parametersMetadata,
  } = props;
  if (!parameter) {
    return null;
  }
  const {name, config = {}, error, warning, system, type: parameterType = 'string'} = parameter;
  const {description, required = false, readOnly = false} = config;
  const normalizedType = String(parameterType).toLowerCase();
  const isBoolean = normalizedType === 'boolean' || normalizedType === 'bool';
  const removeAllowed =
    !readOnly &&
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
        isBoolean && styles.launchFormParameterBoolean,
        name ? `launch-form-parameter-${name}` : undefined,
        getParameterKeyClassName(parameter),
      )}
      style={style}
    >
      <div className={styles.launchFormParameterRow}>
        <ParameterNameInput
          disabled={disabled || (detached && pipeline)}
          rawEdit={rawEdit}
          parameter={parameter}
          onChange={onChange}
          style={{paddingRight: 30}}
          editConfiguration={editConfiguration}
        />
        <div className={styles.launchFormParameterInputRow}>
          <Form.Item
            validateStatus={error ? 'error' : warning ? 'warning' : 'success'}
            hasFeedback
            style={{flex: 1, marginBottom: 0}}
          >
            <LaunchFormParameterInput
              style={{width: '100%', minHeight: isBoolean ? undefined : 32}}
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
              parametersMetadata={parametersMetadata}
            />
          </Form.Item>
          {removeAllowed && typeof onRemoveParameter === 'function' ? (
            <MinusCircleOutlined
              className={classNames('dynamic-delete-button', styles.launchFormParameterRemove)}
              onClick={onRemoveParameterClicked}
            />
          ) : (
            <div className={styles.launchFormParameterRemovePlaceholder}>{'\u00A0'}</div>
          )}
        </div>
      </div>
      {error && (
        <div className="cp-error" style={{margin: 0}}>
          {error}
        </div>
      )}
      {!error && warning && (
        <div className="cp-warning" style={{margin: 0}}>
          {warning}
        </div>
      )}
      {description && (
        <div className="cp-text-not-important">
          <Markdown md={description} className={styles.parameterDescription} />
        </div>
      )}
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
  pipeline: PropTypes.bool,
};

export default LaunchFormParameter;
