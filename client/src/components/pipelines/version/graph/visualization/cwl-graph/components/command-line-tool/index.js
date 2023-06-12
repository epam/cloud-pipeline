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
import {isType} from 'cwlts/models';
import {Button, Icon} from 'antd';
import classNames from 'classnames';
import DockerImageSelector from '../shared/docker-image-selector';
import CodeEditor from '../../../../../../../special/CodeEditor';
import CWLInputPorts from '../shared/cwl-input-ports';
import CWLOutputPorts from '../shared/cwl-output-ports';
import styles from './cwl-command-line-tool.css';

const CommandLineToolEvents = {
  stepCreate: 'step.create',
  stepRemove: 'step.remove',
  stepChange: 'step.change',
  stepUpdate: 'step.update',
  stepChangeId: 'step.change.id',
  stepInPortShow: 'step.inPort.show',
  stepInPortHide: 'step.inPort.hide',
  stepInPortRemove: 'step.inPort.remove',
  stepInPortCreate: 'step.inPort.create',
  stepOutPortRemove: 'step.outPort.remove',
  stepOutPortCreate: 'step.outPort.create',
  stepPortChange: 'step.port.change',
  connectionsUpdated: 'connections.updated',
  inputRemove: 'input.remove',
  inputCreate: 'input.create',
  inputChange: 'input.change',
  outputCreate: 'output.create',
  outputRemove: 'output.remove',
  outputChangeId: 'output.change.id',
  ioChange: 'io.change',
  ioChangeId: 'io.change.id',
  ioChangeType: 'io.change.type',
  validate: 'validate',
  connectionCreate: 'connection.create',
  connectionRemove: 'connection.remove',
  expressionCreate: 'expression.create',
  expressionChange: 'expression.change',
  expressionSerialize: 'expression.serialize'
};

class CWLCommandLineTool extends React.Component {
  componentDidMount () {
    this.toolChanged();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.tool !== prevProps.tool) {
      this.toolChanged();
    }
  }

  componentWillUnmount () {
    this.unsubscribeFromEvents();
  }

  unsubscribeFromEvents = () => {
    if (typeof this._unsubscribeFromEvents === 'function') {
      this._unsubscribeFromEvents();
    }
    this._unsubscribeFromEvents = undefined;
  };

  get currentStep () {
    const {
      tool
    } = this.props;
    if (!tool) {
      return null;
    }
    let step = tool;
    if (/^workflow$/i.test(tool.class)) {
      step = (tool.steps || [])[0];
    }
    return step;
  }

  updateTool = () => {
    const {
      tool,
      step,
      onRedraw,
      onChange
    } = this.props;
    if (step) {
      step.setRunProcess(tool);
      (step.in || []).forEach((input, idx) => {
        if (isType(input, ['File', 'Directory'])) {
          input.isVisible = true;
          step.createWorkflowStepInputModel(input);
        } else {
          input.isVisible = false;
        }
      });
    }
    if (typeof onRedraw === 'function') {
      onRedraw();
    }
    if (typeof onChange === 'function') {
      onChange();
    }
  }

  toolChanged = () => {
    this.unsubscribeFromEvents();
    const {
      tool
    } = this.props;
    const handlers = {
      [CommandLineToolEvents.inputCreate]: this.updateTool,
      [CommandLineToolEvents.inputRemove]: this.updateTool,
      [CommandLineToolEvents.outputCreate]: this.updateTool,
      [CommandLineToolEvents.outputRemove]: this.updateTool,
      [CommandLineToolEvents.inputChange]: this.updateTool,
      [CommandLineToolEvents.outputChangeId]: this.updateTool
    };
    if (tool && tool.eventHub && typeof tool.eventHub.on === 'function') {
      Object.entries(handlers).forEach(([event, handler]) => {
        tool.eventHub.on(event, handler);
      });
    }
    this._unsubscribeFromEvents = () => {
      if (tool && tool.eventHub && typeof tool.eventHub.off === 'function') {
        Object.entries(handlers).forEach(([event, handler]) => {
          tool.eventHub.off(event, handler);
        });
      }
    };
  };

  renderDockerImage = () => {
    const run = this.currentStep;
    if (!run) {
      return null;
    }
    const {
      docker
    } = run;
    if (!docker) {
      return null;
    }
    const onChange = (newDockerImage) => {
      if (docker) {
        docker.dockerPull = newDockerImage;
        this.forceUpdate();
      }
    };
    return (
      <div
        className={styles.propertiesBlock}
      >
        <DockerImageSelector
          className={styles.propertyInput}
          dockerImage={docker ? docker.dockerPull : undefined}
          onChange={onChange}
        />
      </div>
    );
  };

  renderBaseCommand = () => {
    const run = this.currentStep;
    if (!run) {
      return null;
    }
    const {
      baseCommand = []
    } = run;
    const onChangeCode = (index) => (newCode) => {
      run.baseCommand[index] = newCode;
      this.forceUpdate();
    };
    const onRemoveCode = (index) => () => {
      run.baseCommand.splice(index, 1);
      this.forceUpdate();
    };
    const onAddBaseCommand = () => {
      run.baseCommand.push('');
      this.forceUpdate();
    };
    return (
      <div
        className={styles.propertiesBlock}
      >
        <div>
          <b>Base command</b>
        </div>
        {
          baseCommand.map((command, index) => (
            <div
              key={`code-${index}`}
              style={{
                width: '100%'
              }}
            >
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'row',
                  alignItems: 'center',
                  margin: '2px 0'
                }}
              >
                <div
                  style={{flex: 1, overflow: 'auto'}}
                >
                  <CodeEditor
                    code={command}
                    onChange={onChangeCode(index)}
                  />
                </div>
                <Button
                  size="small"
                  onClick={onRemoveCode(index)}
                  type="danger"
                  style={{marginLeft: 5}}
                >
                  <Icon type="delete" />
                </Button>
              </div>
            </div>
          ))
        }
        <div>
          <a onClick={onAddBaseCommand}>
            Add base command
          </a>
        </div>
      </div>
    );
  }

  renderArguments = () => {
    return null;
  };

  renderInputPorts = () => {
    if (!this.currentStep) {
      return null;
    }
    return (
      <div
        className={styles.propertiesBlock}
      >
        <div>
          <b>Input ports</b>
        </div>
        <CWLInputPorts
          step={this.currentStep}
          onChange={this.updateTool}
        />
      </div>
    );
  };

  renderOutputPorts = () => {
    if (!this.currentStep) {
      return null;
    }
    return (
      <div
        className={styles.propertiesBlock}
      >
        <div>
          <b>Output ports</b>
        </div>
        <CWLOutputPorts
          step={this.currentStep}
          onChange={this.updateTool}
        />
      </div>
    );
  };

  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <div
        className={classNames(className, styles.container)}
        style={style}
      >
        {this.renderDockerImage()}
        {this.renderBaseCommand()}
        {this.renderArguments()}
        {this.renderInputPorts()}
        {this.renderOutputPorts()}
      </div>
    );
  }
}

CWLCommandLineTool.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tool: PropTypes.object,
  step: PropTypes.object,
  onRedraw: PropTypes.func,
  onChange: PropTypes.func
};

export default CWLCommandLineTool;
