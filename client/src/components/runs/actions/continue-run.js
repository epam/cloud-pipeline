/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Icon, message, Modal} from 'antd';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import RunName from '../run-name';
import roleModel from '../../../utils/roleModel';
import {PipelineRunner} from '../../../models/pipelines/PipelineRunner';
import Markdown from '../../special/markdown';

function createRunContinuationConfirmationDialog () {
  let instance;

  @inject('preferences', 'routing')
  @observer
  class _RunContinuationConfirmation extends React.Component {
    state = {run: undefined};
    promise = undefined;
    resolve = undefined;

    @computed
    get continueRunMessage () {
      const {preferences} = this.props;
      return preferences.uiContinueRunConfirmation;
    }

    componentDidMount () {
      instance = this;
    }

    open = async (run) => {
      this.close();
      this.promise = new Promise((resolve) => {
        this.resolve = resolve;
      });
      this.setState({run});
      return this.promise;
    };

    onRunCustom = () => {
      if (this.resolve) {
        this.resolve(false);
        this.resolve = undefined;
      }
      const {run} = this.state;
      this.setState({run: undefined});
      const {routing} = this.props;
      if (run && routing) {
        const {
          pipelineId,
          version,
          id
        } = run;
        if (pipelineId && version) {
          routing.push(`/launch/${pipelineId}/${version}/default/${id}?continue=true`);
        } else {
          routing.push(`/launch/${id}?continue=true`);
        }
      }
    };

    onConfirm = () => {
      if (this.resolve) {
        this.resolve(true);
        this.resolve = undefined;
      }
      this.setState({run: undefined});
    };

    close = () => {
      if (this.resolve) {
        this.resolve(false);
        this.resolve = undefined;
      }
      this.setState({run: undefined});
    };

    render () {
      const {run} = this.state;
      const msg = this.continueRunMessage;
      return (
        <Modal
          className="ant-confirm ant-confirm-confirm"
          onCancel={this.close}
          visible={run !== undefined}
          footer={false}
        >
          {
            run && (
              <div className="ant-confirm-body-wrapper">
                <div className="ant-confirm-body">
                  <Icon type="question-circle" />
                  <span className="ant-confirm-title">
                    <div style={{display: 'flex', alignItems: 'center'}}>
                      <span>{'Continue '}</span>
                      <RunName
                        run={run}
                        editable={false}
                        style={{fontWeight: 'bold'}}
                      >
                        run #{run.id}
                      </RunName>
                      <span>?</span>
                    </div>
                  </span>
                  {
                    msg && (
                      <div className="ant-confirm-content">
                        <Markdown md={msg} />
                      </div>
                    )
                  }
                </div>
                <div
                  className="ant-confirm-btns"
                  style={{
                    display: 'flex',
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    float: 'unset',
                    paddingLeft: 40
                  }}
                >
                  <Button size="large" onClick={this.onRunCustom}>
                    Customize
                  </Button>
                  <div style={{
                    display: 'inline-flex',
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexGap: 5
                  }}>
                    <Button size="large" onClick={this.close}>
                      Cancel
                    </Button>
                    <Button
                      size="large"
                      onClick={this.onConfirm}
                      type="primary"
                    >
                      Continue
                    </Button>
                  </div>
                </div>
              </div>
            )
          }
        </Modal>
      );
    }
  }

  function openConfirmation (run) {
    if (instance) {
      return instance.open(run);
    }
    return Promise.resolve(false);
  }

  return {
    RunContinuationConfirmation: _RunContinuationConfirmation,
    openConfirmation
  };
}

const {
  openConfirmation,
  RunContinuationConfirmation
} = createRunContinuationConfirmationDialog();

export const CP_SUPPORT_CONTINUE = 'CP_SUPPORT_CONTINUE';
export const CP_CONTINUE_RUN = 'CP_CONTINUE_RUN';

export function runSupportsContinue (run) {
  if (!run) {
    return false;
  }
  const {
    version,
    pipelineId,
    status = '',
    pipelineRunParameters = []
  } = run;
  const isRemovedPipeline = !!version && !pipelineId;
  const cpSupportContinueParameter = pipelineRunParameters
    .find((p) => p.name === CP_SUPPORT_CONTINUE);
  const cpSupportContinue = cpSupportContinueParameter
    ? `${cpSupportContinueParameter.value}`.toLowerCase() === 'true'
    : false;
  return !isRemovedPipeline &&
    roleModel.executeAllowed(run) &&
    ['FAILURE', 'STOPPED'].includes(status.toUpperCase()) &&
    cpSupportContinue;
}

export async function confirmRunContinuation (run) {
  return openConfirmation(run);
}

export function generateContinueRunParameters (runId, parameters) {
  const params = {...(parameters || {})};
  if (!params[CP_CONTINUE_RUN]) {
    params[CP_CONTINUE_RUN] = {value: `${runId}`, type: 'string'};
  }
  return params;
}

export async function continueRun (run) {
  const hide = message.loading('Continuing run...', 0);
  try {
    const supports = runSupportsContinue(run);
    if (!supports) {
      throw new Error('This run doesn\'t support continuation');
    }
    const {
      id,
      pipelineRunParameters = [],
      instance,
      timeout,
      cmdTemplate,
      nodeCount,
      dockerImage,
      nonPause,
      executionPreferences,
      pipelineId,
      version,
      configName
    } = run;
    let params = {};
    for (const param of pipelineRunParameters) {
      params[param.name] = {
        value: param.value,
        type: param.type
      };
    }
    params = generateContinueRunParameters(id, params);
    const payload = {
      instanceType: instance ? instance.nodeType : undefined,
      hddSize: instance ? instance.nodeDisk : undefined,
      isSpot: instance ? instance.spot : undefined,
      cloudRegionId: instance ? instance.cloudRegionId : undefined,
      timeout,
      cmdTemplate,
      nodeCount,
      dockerImage,
      nonPause,
      executionEnvironment: executionPreferences ? executionPreferences.environment : undefined,
      params,
      pipelineId,
      version,
      configurationName: configName
    };
    const request = new PipelineRunner();
    await request.send({...payload, force: true});
    if (request.error) {
      throw new Error(request.error);
    }
    const {value} = request;
    return value ? value.id : undefined;
  } catch (error) {
    message.error(
      (
        <span>
          <span>Error continuing run:</span>
          <b>{error.message}</b>
        </span>
      ),
      5
    );
  } finally {
    hide();
  }
  return undefined;
}

export {RunContinuationConfirmation};
