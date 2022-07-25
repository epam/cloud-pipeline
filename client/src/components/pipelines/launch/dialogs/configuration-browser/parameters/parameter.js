/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {
  Checkbox,
  Icon,
  Input,
  InputNumber,
  Select
} from 'antd';
import {observer} from 'mobx-react';
import Hint from './hint';
import BucketBrowser from '../../BucketBrowser';
import {injectParametersStore} from './store';
import styles from './parameters.css';
import {ParametersAutoCompleteInput} from './auto-complete-input';

function ParameterRow ({className, style, children, error, noPadding}) {
  return (
    <div
      className={
        classNames(
          styles.parameter,
          {
            [styles.padding]: !noPadding,
            [styles.error]: !!error,
            'cp-error': !!error
          },
          className
        )
      }
      style={style}
    >
      {children}
    </div>
  );
}

ParameterRow.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  error: PropTypes.oneOfType([PropTypes.any, PropTypes.bool]),
  noPadding: PropTypes.bool
};

function ParameterName ({className, style, children, error}) {
  return (
    <span
      className={
        classNames(
          styles.name,
          'parameter-name',
          {
            'cp-error': !!error
          },
          className
        )
      }
      style={style}
    >
      {children}
    </span>
  );
}

ParameterName.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  error: PropTypes.oneOfType([PropTypes.any, PropTypes.bool])
};

function ParameterValue ({className, style, children, error}) {
  return (
    <div
      className={
        classNames(
          styles.value,
          {
            'cp-error': !!error
          },
          'parameter-value',
          className
        )
      }
      style={style}
    >
      {children}
    </div>
  );
}

ParameterValue.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  error: PropTypes.oneOfType([PropTypes.any, PropTypes.bool])
};

class Parameter extends React.Component {
  state = {
    bucketBrowserVisible: false
  };

  get readOnly () {
    const {
      parameter
    } = this.props;
    if (!parameter) {
      return true;
    }
    const {
      value,
      no_override: _noOverride,
      noOverride = _noOverride,
      read_only: _readOnly = noOverride && value !== undefined,
      readonly = _readOnly,
      readOnly = readonly
    } = parameter;
    return readOnly;
  }

  openBucketBrowser = () => {
    this.setState({bucketBrowserVisible: true});
  };

  closeBucketBrowser = () => {
    this.setState({bucketBrowserVisible: false});
  };

  render () {
    const {
      id,
      className,
      style,
      parameter,
      disabled,
      editable,
      larger,
      onChange,
      onRemove,
      parametersStore
    } = this.props;
    if (!parameter || !parametersStore) {
      return null;
    }
    const {
      bucketBrowserVisible
    } = this.state;
    const {
      name,
      value,
      type,
      required,
      description,
      system,
      restricted,
      nameError,
      valueError
    } = parameter;
    const readOnly = this.readOnly;
    const enumeration = parametersStore.getParameterEnumeration(parameter);
    const remove = (e) => {
      if (e) {
        e.stopPropagation();
        e.preventDefault();
      }
      if (typeof onRemove === 'function') {
        onRemove();
      }
    };
    const changeName = (e) => {
      if (typeof onChange === 'function') {
        onChange({
          ...parameter,
          name: e.target.value
        });
      }
    };
    const changeValue = (newValue) => {
      if (typeof onChange === 'function') {
        onChange({
          ...parameter,
          value: newValue
        });
      }
    };
    const changePathValue = (newValue) => {
      if (typeof onChange === 'function') {
        onChange({
          ...parameter,
          value: newValue
        });
      }
      this.closeBucketBrowser();
    };
    const changeBooleanValue = (e) => changeValue(e.target.checked);
    const renderParameterName = () => {
      if (system || required || readOnly || disabled || !editable) {
        return (
          <ParameterName
            error={!!nameError}
          >
            {name}
          </ParameterName>
        );
      }
      return (
        <Input
          value={name}
          className={
            classNames(
              styles.name,
              styles.input,
              {
                'cp-error': !!nameError
              },
              'parameter-name'
            )
          }
          onChange={changeName}
        />
      );
    };
    const isPathParameter = /^(input|output|common|path)$/i.test(type);
    const renderParameterValue = () => {
      const className = classNames(
        styles.value,
        {
          'cp-error': !!valueError
        },
        'parameter-value'
      );
      const valueDisabled = restricted || readOnly || disabled;
      let typeCorrected = (type || 'string').toLowerCase();
      if (enumeration && enumeration.length > 0) {
        typeCorrected = 'enum';
      }
      switch (typeCorrected) {
        case 'input':
        case 'path':
        case 'output':
        case 'common':
          let icon = 'folder';
          switch (typeCorrected) {
            case 'input': icon = 'download'; break;
            case 'output': icon = 'upload'; break;
            case 'common': icon = 'select'; break;
            default: icon = 'folder'; break;
          }
          return (
            <div
              className={className}
            >
              <ParametersAutoCompleteInput
                disabled={valueDisabled}
                value={value}
                className={
                  classNames(
                    {
                      'cp-error': !!valueError
                    }
                  )
                }
                error={valueError}
                onChange={changeValue}
                addonBefore={(
                  <Icon
                    type={icon}
                    style={{cursor: 'pointer'}}
                    onClick={this.openBucketBrowser}
                  />
                )}
                style={{width: '100%'}}
              />
            </div>
          );
        case 'enum':
          return (
            <Select
              allowClear={!required}
              value={value}
              className={className}
              onChange={changeValue}
              getPopupContainer={node => node.parentNode}
            >
              {
                enumeration.map(option => (
                  <Select.Option key={option} value={option}>
                    {option}
                  </Select.Option>
                ))
              }
            </Select>
          );
        case 'int':
          return (
            <InputNumber
              disabled={valueDisabled}
              value={value}
              className={className}
              onChange={changeValue}
            />
          );
        case 'boolean':
          return (
            <Checkbox
              disabled={valueDisabled}
              className={className}
              checked={Boolean(value)}
              onChange={changeBooleanValue}
            >
              Enabled
            </Checkbox>
          );
        case 'string':
        default:
          return (
            <ParametersAutoCompleteInput
              disabled={valueDisabled}
              value={value}
              className={className}
              onChange={changeValue}
              error={valueError}
            />
          );
      }
    };
    const renderRemoveButton = () => {
      const removeAllowed = !required && !disabled && editable;
      return (
        <div
          className={styles.remove}
        >
          {
            removeAllowed && (
              <Icon
                type="minus-circle-o"
                className={styles.button}
                onClick={remove}
              />
            )
          }
          {
            !removeAllowed && '\u00A0'
          }
        </div>
      );
    };
    return (
      <div
        id={id}
        className={
          classNames(
            className,
            styles.parameterContainer,
            {
              [styles.larger]: larger
            },
            'parameter'
          )
        }
        style={style}
      >
        <ParameterRow noPadding>
          {renderParameterName()}
          {renderParameterValue()}
          {renderRemoveButton()}
          <Hint>
            {description}
          </Hint>
          {
            isPathParameter && (
              <BucketBrowser
                multiple
                onSelect={changePathValue}
                onCancel={this.closeBucketBrowser}
                visible={bucketBrowserVisible}
                path={value}
                showOnlyFolder={/^output$/i.test(type)}
                allowBucketSelection={/^path$/i.test(type)}
                checkWritePermissions={/^output$/i.test(type)}
                bucketTypes={['AZ', 'S3', 'GS', 'DTS', 'NFS']}
              />
            )
          }
        </ParameterRow>
        {
          (nameError || valueError) && (
            <ParameterRow error>
              <ParameterName>
                {
                  nameError || '\u00A0'
                }
              </ParameterName>
              <ParameterValue>
                {
                  valueError || '\u00A0'
                }
              </ParameterValue>
            </ParameterRow>
          )
        }
      </div>
    );
  };
}

Parameter.propTypes = {
  id: PropTypes.string,
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  disabled: PropTypes.bool,
  editable: PropTypes.bool,
  larger: PropTypes.bool
};

Parameter.defaultProps = {
  editable: true
};

export {
  ParameterRow,
  ParameterName,
  ParameterValue
};
export default injectParametersStore(observer(Parameter));
