/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import ReactDOM from 'react-dom';
import {observable} from 'mobx';
import {Provider, observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {Alert, Button, Checkbox, Icon, message, Modal, Row} from 'antd';
import moment from 'moment-timezone';
import CommitRunForm from '../logs/forms/CommitRunForm';
import PipelineRunCommit from '../../../models/pipelines/PipelineRunCommit';
import StopPipeline from '../../../models/pipelines/StopPipeline';
import TerminatePipeline from '../../../models/pipelines/TerminatePipeline';
import getCommitAllowedForTool from './get-commit-allowed-for-tool';
import {diskSizeAllowsPause} from './warnings/disk-size-warning';

export function canStopRun (run) {
  // Checks only run state, not user permissions
  const {status, commitStatus} = run;
  return status.toLowerCase() === 'running' && (commitStatus || '').toLowerCase() !== 'committing';
}

export function runIsCommittable (run) {
  // Checks only run state, not user permissions
  const {podIP, platform} = run;
  return podIP && !(run.nodeCount > 0) &&
    !(run.parentRunId && run.parentRunId > 0) && !/^windows$/i.test(platform);
}

export function canCommitRun (run) {
  // Checks only run state, not user permissions
  return canStopRun(run) && runIsCommittable(run);
}

export function checkCommitAllowedForTool (dockerImage, dockerRegistries) {
  if (!dockerRegistries) {
    return Promise.resolve(true);
  }
  return new Promise((resolve) => {
    dockerRegistries
      .fetchIfNeededOrWait()
      .then(() => {
        const [registry, group, toolAndVersion] = (dockerImage || '').split('/');
        const [imageRegistry] = (dockerRegistries.value.registries || [])
          .filter(r => r.path === registry);
        if (imageRegistry) {
          const [imageGroup] = (imageRegistry.groups || [])
            .filter(g => g.name === group);
          if (imageGroup) {
            const [image] = toolAndVersion.split(':');
            const [tool] = (imageGroup.tools || [])
              .filter(i => i.image === `${group}/${image}`);
            return Promise.resolve(tool);
          }
        }
        return Promise.resolve(undefined);
      })
      .then((tool) => {
        if (tool) {
          const [, version = 'latest'] = (dockerImage || '').split('/').pop().split(':');
          return getCommitAllowedForTool(tool.id, version);
        }
        return Promise.resolve(false);
      })
      .then(resolve)
      .catch(() => resolve(false));
  });
}

export function canPauseRun (run, preferences) {
  // Checks only run state, not user permissions
  const {instance, pipelineRunParameters, podIP, initialized} = run;
  return canStopRun(run) && initialized &&
    instance && instance.spot !== undefined && !instance.spot &&
    podIP && !(run.nodeCount > 0) &&
    !(run.parentRunId && run.parentRunId > 0) &&
    (pipelineRunParameters || []).filter(r => {
      return (r.name === 'CP_CAP_AUTOSCALE' && r.value === 'true');
    }).length === 0 &&
    diskSizeAllowsPause(
      preferences,
      instance ? instance.nodeDisk : 0
    );
}

export function stopRun (parent, callback) {
  if (!parent) {
    console.warn('"stopRun" function should be called with parent component passed to arguments:');
    console.warn('"stopRun(parent)"');
    console.warn('Parent component should be marked with @runPipelineActions');
    // eslint-disable-next-line max-len
    throw new Error('"stopRun" function should be called with parent component passed to arguments:');
  }
  const {
    localization,
    dockerRegistries,
    hiddenObjects,
    preferences
  } = parent.props;
  return function (run) {
    return stopRunFn(run, callback, {preferences, localization, dockerRegistries, hiddenObjects});
  };
}

export function terminateRun (parent, callback) {
  if (!parent) {
    // eslint-disable-next-line max-len
    console.warn('"terminateRun" function should be called with parent component passed to arguments:');
    console.warn('"terminateRun(parent)"');
    console.warn('Parent component should be marked with @runPipelineActions');
    // eslint-disable-next-line max-len
    throw new Error('"terminateRun" function should be called with parent component passed to arguments:');
  }
  const {
    localization,
    dockerRegistries,
    hiddenObjects
  } = parent.props;
  return function (run) {
    return terminateRunFn(run, callback, {localization, dockerRegistries, hiddenObjects});
  };
}

async function stopPipeline (run) {
  const hide = message.loading('Terminating run...', 0);
  const request = new StopPipeline(run.id);
  await request.send(
    {
      endDate: moment.utc().format('YYYY-MM-DD HH:mm:ss.SSS'),
      status: 'STOPPED'
    }
  );
  hide();
  return request.error;
}

async function terminatePipeline (run) {
  const hide = message.loading('Terminating run...', 0);
  const request = new TerminatePipeline(run.id);
  await request.send({});
  hide();
  return request.error;
}

async function commitRunAndStop (run, payload) {
  const {newImageName, registryToCommitId} = payload;
  const request = new PipelineRunCommit(run.id);
  const hide = message.loading('Committing...', -1);
  await request.send({
    deleteFiles: false,
    newImageName,
    registryToCommitId,
    stopPipeline: true
  });
  hide();
  return request.error;
}

function stopRunFn (run, callback, stores) {
  let content;
  const {
    preferences
  } = stores || {};
  let maintenanceMode = false;
  if (preferences && preferences.loaded) {
    maintenanceMode = preferences.systemMaintenanceMode;
  }
  const canCommitRunResult = canCommitRun(run);
  const onOkClicked = async (close, resolve) => {
    let validationResult = true;
    if (content) {
      validationResult = await content.validate();
    }
    if (validationResult) {
      let error;
      if (validationResult.persistState && validationResult.canCommitRun) {
        error = await commitRunAndStop(run, validationResult.values);
      } else {
        error = await stopPipeline(run);
      }
      if (error) {
        message.error(error, 5);
      }
      close();
      callback && callback();
      resolve(!error);
    }
  };
  return new Promise((resolve) => {
    Modal.confirm({
      title: `Stop ${run.podId}?`,
      width: '50%',
      okText: 'STOP',
      okType: 'danger',
      content: (
        <Provider {...stores}>
          <StopRunConfirmation
            ref={(el) => {
              content = el;
            }}
            runId={run.id}
            canCommitRun={canCommitRunResult}
            dockerImage={run.dockerImage}
            dockerRegistries={stores.dockerRegistries}
            maintenanceMode={maintenanceMode}
          />
        </Provider>
      ),
      onOk (close) {
        onOkClicked(close, resolve);
      },
      onCancel () {
        resolve();
      }
    });
  });
}

@observer
class TerminateRunDialog extends React.Component {
  static propTypes = {
    onInitialized: PropTypes.func
  };
  state = {
    pending: false,
    visible: false
  };
  @observable run;
  @observable onClose;
  @observable displayPromiseResolve;
  onTerminateClicked = () => {
    this.setState({
      pending: true
    }, async () => {
      const error = await terminatePipeline(this.run);
      if (error) {
        message.error(error, 5);
      }
      this.setState({
        visible: false
      }, () => {
        this.displayPromiseResolve && this.displayPromiseResolve(!error);
      });
    });
  };
  display = async (run) => {
    this.run = run;
    this.setState({
      visible: true,
      pending: false
    });
    return new Promise((resolve) => {
      this.displayPromiseResolve = resolve;
    });
  };
  hide = () => {
    this.setState({
      visible: false,
      pending: false
    }, () => {
      this.displayPromiseResolve && this.displayPromiseResolve(true);
    });
  };
  componentDidMount () {
    this.props.onInitialized && this.props.onInitialized(this);
  }
  render () {
    if (!this.run) {
      return null;
    }
    return (
      <Modal
        footer={null}
        closable={false}
        title={null}
        width="50%"
        visible={this.state.visible}>
        <div>
          <Row style={{marginBottom: 10}} type="flex" align="middle">
            <Icon
              type="question-circle"
              className="cp-stop-run-modal-confirm-icon"
              style={{fontSize: 'x-large', marginLeft: 20}} />
            <b
              style={{marginLeft: 10, fontSize: 14}}>Terminate {this.run.podId}?</b>
          </Row>
          <Row type="flex" style={{marginBottom: 5, marginLeft: 55}}>
            <Alert
              type="info"
              showIcon
              message="Once a run is terminated - all local data will be deleted (that is not stored within shared data storages)" />
          </Row>
          <Row type="flex" justify="end" style={{marginTop: 10}}>
            <Button
              disabled={this.state.pending}
              onClick={this.hide}>
              CANCEL
            </Button>
            <Button
              disabled={this.state.pending}
              type="danger"
              style={{marginLeft: 10}}
              onClick={this.onTerminateClicked}>
              TERMINATE
            </Button>
          </Row>
        </div>
      </Modal>
    );
  }
}

let terminateRunDialogInstance;

const initializeTerminateRunDialog = (dialog) => {
  terminateRunDialogInstance = dialog;
};

const terminateRunDialogContainer = document.createElement('div');
document.body.appendChild(terminateRunDialogContainer);

ReactDOM.render(
  <TerminateRunDialog onInitialized={initializeTerminateRunDialog} />,
  terminateRunDialogContainer
);

function terminateRunFn (run, callback) {
  return new Promise(async (resolve) => {
    const success = await terminateRunDialogInstance.display(run);
    callback && callback();
    resolve(success);
  });
}

@observer
class StopRunConfirmation extends React.Component {
  static propTypes = {
    runId: PropTypes.number,
    canCommitRun: PropTypes.bool,
    dockerImage: PropTypes.string,
    isTermination: PropTypes.bool,
    dockerRegistries: PropTypes.object,
    maintenanceMode: PropTypes.bool
  };

  state = {
    persistState: false,
    commitAllowed: false
  };

  _commitRunForm;

  componentDidMount () {
    const {dockerImage, dockerRegistries} = this.props;
    checkCommitAllowedForTool(dockerImage, dockerRegistries)
      .then(allowed => this.setState({commitAllowed: allowed}));
  }

  onChange = (e) => {
    this.setState({
      persistState: e.target.checked
    });
  };

  validate = async () => {
    const {canCommitRun} = this.props;
    const {commitAllowed} = this.state;
    if (this.state.persistState) {
      if (this._commitRunForm) {
        const result = await this._commitRunForm.validate();
        if (result) {
          return {
            persistState: true,
            canCommitRun: canCommitRun && commitAllowed,
            values: result
          };
        }
      }
      return null;
    }
    return {
      persistState: false,
      canCommitRun: canCommitRun && commitAllowed
    };
  };

  onInitializeForm = (component) => {
    this._commitRunForm = component;
  };

  render () {
    const {commitAllowed} = this.state;
    const {maintenanceMode} = this.props;
    return (
      <div>
        <Row type="flex" style={{marginBottom: 5}}>
          <Alert
            type="info"
            showIcon
            message={`Once a run is ${this.props.isTermination ? 'terminated' : 'stopped'} - all local data will be deleted (that is not stored within shared data storages)`} />
        </Row>
        {
          this.props.canCommitRun && commitAllowed && !maintenanceMode &&
          <Row type="flex" style={{marginBottom: 5, fontWeight: 'bold'}}>
            Do you want to persist current docker image state?
          </Row>
        }
        {
          this.props.canCommitRun && commitAllowed && !maintenanceMode &&
          <Row type="flex" style={{marginBottom: 5}}>
            <Checkbox
              checked={this.state.persistState}
              onChange={this.onChange}>
              Persist current docker image state
            </Checkbox>
          </Row>
        }
        {
          this.state.persistState &&
          this.props.canCommitRun &&
          commitAllowed &&
          !maintenanceMode && (
            <CommitRunForm
              runId={this.props.runId}
              onInitialized={this.onInitializeForm}
              visible={this.state.persistState}
              stopPipeline
              displayStopPipelineSelector={false}
              displayDeleteRuntimeFilesSelector={false}
              defaultDockerImage={this.props.dockerImage}
            />
          )
        }
      </div>
    );
  }
}
