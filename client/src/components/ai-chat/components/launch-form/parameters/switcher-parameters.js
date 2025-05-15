/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  BooleanParameter,
  InputPathParameter,
  MetadataParameter,
  OutputPathParameter,
  PathParameter,
  StringParameter
} from '../index';

export default class SwitcherParameters extends React.Component {
  renderParameter () {
    const {type, value, onChange, label, disabled} = this.props;

    switch (type) {
      case 'string':
        return (
          <StringParameter
            value={value}
            onChange={onChange}
            label={label}
            disabled={disabled}
          />
        );
      case 'boolean':
        return (
          <BooleanParameter
            value={typeof value === 'boolean' ? String(value) : value}
            onChange={onChange}
            label={label}
            disabled={disabled}
          />
        );
      case 'path':
        return (
          <PathParameter
            value={value}
            onChange={onChange}
            label={label}
            disabled={disabled}
          />
        );
      case 'inputPath':
        return (
          <InputPathParameter
            value={value}
            onChange={onChange}
            label={label}
            disabled={disabled}
          />
        );
      case 'outputPath':
        return (
          <OutputPathParameter
            value={value}
            onChange={onChange}
            label={label}
            disabled={disabled}
          />
        );
      case 'metadata':
        return (
          <MetadataParameter
            value={value}
            onChange={onChange}
            label={label}
            disabled={disabled}
          />
        );
      default:
        return <div>Unknown parameter type: {type}</div>;
    }
  }

  render () {
    return <div>{this.renderParameter()}</div>;
  }
}

SwitcherParameters.propTypes = {
  type: PropTypes.oneOf([
    'string',
    'boolean',
    'path',
    'inputPath',
    'outputPath',
    'metadata'
  ]).isRequired,
  value: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.bool
  ]),
  onChange: PropTypes.func.isRequired,
  label: PropTypes.string,
  disabled: PropTypes.bool
};

SwitcherParameters.defaultProps = {
  value: '',
  label: '',
  disabled: false
};
