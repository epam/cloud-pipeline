/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Icon,
  Input,
  Select
} from 'antd';

const Types = {
  any: 'any',
  file: 'File',
  directory: 'Directory',
  array: 'array',
  enum: 'emum',
  record: 'record',
  string: 'string',
  int: 'int',
  float: 'float',
  boolean: 'boolean',
  map: 'map'
};

const AllTypes = Object.values(Types);

class CWLPort extends React.Component {
  onChangeId = (event) => {
    const {
      port,
      onChange
    } = this.props;
    if (!port || typeof onChange !== 'function') {
      return;
    }
    port.id = event.target.value;
    onChange(port);
  };
  onChangeType = (newType) => {
    const {
      port,
      onChange
    } = this.props;
    if (
      !port ||
      !port.type ||
      typeof port.type.setType !== 'function' ||
      typeof onChange !== 'function'
    ) {
      return;
    }
    port.type.setType(newType);
    onChange(port);
  };
  onDelete = () => {
    const {
      onRemove
    } = this.props;
    if (typeof onRemove !== 'function') {
      return;
    }
    onRemove();
  };
  render () {
    const {
      className,
      style,
      port,
      disabled
    } = this.props;
    const {
      id,
      type
    } = port || {};
    return (
      <div
        className={className}
        style={{
          ...(style || {}),
          display: 'flex',
          alignItems: 'center'
        }}
      >
        <Input
          disabled={disabled}
          style={{flex: 1}}
          value={id}
          onChange={this.onChangeId}
        />
        <Select
          style={{marginLeft: 5, width: 150}}
          value={type ? type.type : undefined}
          onChange={this.onChangeType}
        >
          {
            AllTypes.map((aType) => (
              <Select.Option key={aType} value={aType}>
                {aType}
              </Select.Option>
            ))
          }
        </Select>
        <Button
          disabled={disabled}
          size="small"
          type="danger"
          onClick={this.onDelete}
          style={{marginLeft: 5}}
        >
          <Icon type="delete" />
        </Button>
      </div>
    );
  }
}

CWLPort.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  output: PropTypes.bool,
  input: PropTypes.bool,
  disabled: PropTypes.bool,
  port: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func
};

export default CWLPort;
