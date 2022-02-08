/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
  Button, Checkbox,
  Icon,
  Select
} from 'antd';
import styles from '../color-variable/color-variable.css';

const IconSet = {
  AWS: [
    {name: 'Light', value: 'aws-light.svg'},
    {name: 'Default', value: 'aws.svg'}
  ],
  GCP: [
    {name: 'Transparent', value: 'gcp-light.svg'},
    {name: 'Default', value: 'gcp.svg'}
  ],
  AZURE: [
    {name: 'Default', value: 'azure.svg'}
  ]
};

function getIcon (value, iconSet = []) {
  const e = /^@static_resource\('icons\/providers\/(.*)'\)$/i.exec(value);
  if (e) {
    const parsed = e[1];
    const icon = iconSet.find(o => (o.value || '').toLowerCase() === parsed.toLowerCase());
    return icon ? icon.value : undefined;
  }
  return undefined;
}

function ProviderIcon (
  {
    className,
    disabled,
    error,
    extended,
    initialValue,
    modifiedValue,
    onChange,
    provider,
    value,
    variable
  }
) {
  const iconSet = provider ? IconSet[provider] : [];
  if (!iconSet || !iconSet.length) {
    return null;
  }
  const parsedValue = getIcon(value, iconSet);
  const handleChange = (e) => {
    if (onChange) {
      onChange(variable, `@static_resource('icons/providers/${e}')`);
    }
  };
  const onClear = () => {
    if (onChange) {
      onChange(variable, undefined);
    }
  };
  const onRevert = () => {
    if (onChange) {
      onChange(variable, initialValue);
    }
  };
  return (
    <div
      className={
        classNames(
          className
        )
      }
    >
      <Select
        disabled={
          disabled ||
          (parsedValue && iconSet.length === 1)
        }
        className={
          classNames(
            {
              'cp-error': error
            }
          )
        }
        placeholder={`Pick ${provider} icon...`}
        value={parsedValue}
        style={{width: 250}}
        onChange={handleChange}
      >
        {
          iconSet.map(icon => (
            <Select.Option
              key={icon.value}
              value={icon.value}
            >
              {icon.name}
            </Select.Option>
          ))
        }
      </Select>
      {
        initialValue !== modifiedValue && (
          <Button
            disabled={disabled}
            onClick={onRevert}
            className={classNames(styles.button, styles.small)}
          >
            <Icon type="rollback" />
          </Button>
        )
      }
      <Checkbox
        disabled={!extended || disabled}
        checked={!extended}
        className={styles.button}
        onChange={e => e.target.checked ? onClear() : undefined}
      >
        Inherited
      </Checkbox>
    </div>
  );
}

ProviderIcon.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  variable: PropTypes.string,
  error: PropTypes.bool,
  value: PropTypes.string,
  modifiedValue: PropTypes.string,
  initialValue: PropTypes.string,
  extended: PropTypes.bool,
  parsedValues: PropTypes.object,
  parsedValue: PropTypes.string,
  onChange: PropTypes.func,
  provider: PropTypes.string
};

export default ProviderIcon;
