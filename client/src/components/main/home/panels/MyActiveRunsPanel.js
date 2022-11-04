/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Link} from 'react-router';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {
  Alert,
  message,
  Modal,
  Row,
  Button
} from 'antd';
import PausePipeline from '../../../../models/pipelines/PausePipeline';
import {
  PipelineRunCommitCheck,
  PIPELINE_RUN_COMMIT_CHECK_FAILED
} from '../../../../models/pipelines/PipelineRunCommitCheck';
import PipelineRunLayers, {LAYERS_EXCEEDED_DISCLAIMER}
  from '../../../../models/pipelines/PipelineRunLayers';
import ResumePipeline from '../../../../models/pipelines/ResumePipeline';
import CardsPanel from './components/CardsPanel';
import renderRunCard from './components/renderRunCard';
import getRunActions from './components/getRunActions';
import LoadingView from '../../../special/LoadingView';
import ModalConfirm from '../../../special/ModalConfirm';
import localization from '../../../../utils/localization';
import {openReRunForm, runPipelineActions, stopRun, terminateRun} from '../../../runs/actions';
import mapResumeFailureReason from '../../../runs/utilities/map-resume-failure-reason';
import roleModel from '../../../../utils/roleModel';
import pipelineRunSSHCache from '../../../../models/pipelines/PipelineRunSSHCache';
import VSActions from '../../../versioned-storages/vs-actions';
import styles from './Panel.css';

@roleModel.authenticationInfo
@localization.localizedComponent
@runPipelineActions
@inject('pipelines', 'multiZoneManager', 'preferences')
@VSActions.check
@observer
export default class MyActiveRunsPanel extends localization.LocalizedReactComponent {
  static propTypes = {
    panelKey: PropTypes.string,
    activeRuns: PropTypes.object,
    onInitialize: PropTypes.func,
    refresh: PropTypes.func
  };

  state = {
    hovered: undefined,
    showModalConfirmDialog: null
  };

  @computed
  get commitMaxLayers () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return preferences.commitMaxLayers;
    }
    return undefined;
  }

  get usesActiveRuns () {
    return true;
  }

  getActiveRuns = () => {
    if (this.props.activeRuns.loaded) {
      return (this.props.activeRuns.value || [])
        .map(mapResumeFailureReason)
        .filter(r => ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'].indexOf(r.status) >= 0);
    }
    return [];
  };

  navigateToRun = ({id}) => {
    this.props.router && this.props.router.push(`/run/${id}`);
  };

  confirm = (warning, actionText, action) => {
    Modal.confirm({
      title: warning,
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => action(),
      okText: actionText,
      cancelText: 'CANCEL'
    });
  };

  confirmPause = async (run, warning, actionText, action) => {
    const checkRequest = new PipelineRunCommitCheck(run.id);
    const layersRequest = new PipelineRunLayers(run.id);
    await Promise.all([
      checkRequest.fetch(),
      layersRequest.fetch()
    ]);
    const layersExceeded = layersRequest.loaded &&
      layersRequest.value &&
      !layersRequest.error &&
      this.commitMaxLayers &&
      layersRequest.value >= this.commitMaxLayers;
    const alerts = [];
    if (checkRequest.loaded && !checkRequest.value) {
      alerts.push(
        <Alert
          type="error"
          key="checkRequestError"
          message={PIPELINE_RUN_COMMIT_CHECK_FAILED}
          style={{marginBottom: 5}}
        />
      );
    }
    if (layersExceeded) {
      alerts.push(
        <Alert
          type="error"
          key="layersExceeded"
          message={LAYERS_EXCEEDED_DISCLAIMER}
          style={{marginBottom: 5}}
        />
      );
    }
    this.setState({
      showModalConfirmDialog: {
        run,
        alerts,
        warning,
        actionText,
        action,
        okDisabled: layersExceeded
      }
    });
  };

  closeModalConfirmDialog = () => {
    this.setState({showModalConfirmDialog: null});
  };

  pauseRun = async (run) => {
    const hide = message.loading('Pausing...', -1);
    const request = new PausePipeline(run.id);
    await request.send({});
    this.closeModalConfirmDialog();
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      this.props.refresh && await this.props.refresh();
      hide();
    }
  };

  resumeRun = async (run) => {
    const hide = message.loading('Resuming...', -1);
    const request = new ResumePipeline(run.id);
    await request.send({});
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      this.props.refresh && await this.props.refresh();
      hide();
    }
  };

  reRun = (run) => {
    return openReRunForm(run, this.props);
  };

  renderContent = () => {
    let content;
    if (!this.props.activeRuns.loaded && this.props.activeRuns.pending) {
      content = <LoadingView />;
    } else if (this.props.activeRuns.error) {
      content = <Alert type="warning" message={this.props.activeRuns.error} />;
    } else {
      content = [
        <Row key="runs" style={{flex: 1, overflowY: 'auto'}}>
          <CardsPanel
            key="runs"
            hovered={this.state.hovered}
            panelKey={this.props.panelKey}
            onClick={this.navigateToRun}
            emptyMessage="There are no active runs"
            actions={
              getRunActions(
                this.props,
                {
                  pause: run => this.confirmPause(
                    run,
                    `Are you sure you want to pause run ${run.podId}?`,
                    'PAUSE',
                    () => this.pauseRun(run)
                  ),
                  resume: run => this.confirm(
                    `Are you sure you want to resume run ${run.podId}?`,
                    'RESUME',
                    () => this.resumeRun(run)
                  ),
                  stop: stopRun(this, this.props.refresh),
                  terminate: terminateRun(this, this.props.refresh),
                  run: this.reRun,
                  openUrl: (url, target = '_blank') => {
                    window.open(url, target).focus();
                  },
                  ssh: async run => {
                    const runSSH = pipelineRunSSHCache.getPipelineRunSSH(run.id);
                    await runSSH.fetchIfNeededOrWait();

                    if (runSSH.loaded) {
                      window.open(runSSH.value, '_blank').focus();
                    }
                    if (runSSH.error) {
                      message.error(runSSH.error);
                    }
                  },
                  vsActionsMenu: (run, visible) => {
                    this.setState({
                      hovered: visible ? run : undefined
                    });
                  }
                })
            }
            cardClassName={run => classNames({
              'cp-card-service': run.initialized && run.serviceUrl
            })}
            childRenderer={renderRunCard}>
            {this.getActiveRuns()}
          </CardsPanel>
        </Row>,
        this.getActiveRuns().length > 0 &&
        <Row
          key="explore other"
          type="flex"
          justify="center"
          style={{margin: 10}}>
          <span
            style={{
              fontStyle: 'italic'
            }}>
            Top {this.props.activeRuns.params.pageSize} runs will be shown. <Link to="/runs/active">Explore all active runs</Link>
          </span>
        </Row>
      ];
    }
    return content;
  };

  render () {
    const {showModalConfirmDialog} = this.state;
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return (<Alert type="warning" message={this.props.authenticatedUserInfo.error} />);
    }
    return (
      <div className={styles.container}>
        {this.renderContent()}
        {showModalConfirmDialog ? (
          <ModalConfirm
            onOk={showModalConfirmDialog.action}
            onCancel={this.closeModalConfirmDialog}
            visible={!!showModalConfirmDialog}
            title={showModalConfirmDialog.warning}
            footer={(
              <div>
                <Button
                  onClick={this.closeModalConfirmDialog}
                >
                  CANCEL
                </Button>
                <Button
                  disabled={showModalConfirmDialog.okDisabled}
                  type="primary"
                  onClick={showModalConfirmDialog.action}
                >
                  {showModalConfirmDialog.actionText}
                </Button>
              </div>
            )}
          >
            {showModalConfirmDialog.alerts}
          </ModalConfirm>
        ) : null}
      </div>
    );
  }

  update () {
    this.forceUpdate();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }
}
