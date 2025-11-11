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
import CWLPort from '../cwl-port';

class CWLOutputPorts extends React.Component {
  onAddPort = () => {
    const {
      step,
      onChange
    } = this.props;
    if (step && typeof step.addOutput === 'function') {
      step.addOutput({
        type: 'File',
        outputBinding: {
          glob: '*'
        }
      });
    }
    this.forceUpdate();
    if (typeof onChange === 'function') {
      onChange();
    }
  };
  onChangePort = (index) => () => {
    const {
      onChange
    } = this.props;
    this.forceUpdate();
    if (typeof onChange === 'function') {
      onChange();
    }
  };
  onRemovePort = (index) => () => {
    const {
      step,
      onChange
    } = this.props;
    const {
      outputs = []
    } = step || {};
    if (
      outputs &&
      outputs.length > index &&
      typeof step.removeOutput === 'function'
    ) {
      step.removeOutput(outputs[index]);
    }
    this.forceUpdate();
    if (typeof onChange === 'function') {
      onChange();
    }
  };

  render () {
    const {
      className,
      style,
      step,
      disabled
    } = this.props;
    const {
      outputs = []
    } = step || {};
    return (
      <div
        className={className}
        style={style}
      >
        {
          outputs.map((input, index) => (
            <CWLPort
              disabled={disabled}
              key={`input-port-${index}`}
              port={input}
              onChange={this.onChangePort(index)}
              onRemove={this.onRemovePort(index)}
              style={{margin: '2px 0'}}
            />
          ))
        }
        {
          !disabled && (
            <div style={{margin: '2px 0'}}>
              <a onClick={this.onAddPort}>Add output port</a>
            </div>
          )
        }
      </div>
    );
  }
}

CWLOutputPorts.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  step: PropTypes.object,
  onChange: PropTypes.func
};

export default CWLOutputPorts;
