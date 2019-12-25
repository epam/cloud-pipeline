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
import PropTypes from 'prop-types';
import {Link} from 'react-router';
import {inject, observer} from 'mobx-react';
import PausePipeline from '../../../../models/pipelines/PausePipeline';
import {
  PipelineRunCommitCheck,
  PIPELINE_RUN_COMMIT_CHECK_FAILED
} from '../../../../models/pipelines/PipelineRunCommitCheck';
import ResumePipeline from '../../../../models/pipelines/ResumePipeline';
import CardsPanel from './components/CardsPanel';
import renderRunCard from './components/renderRunCard';
import getRunActions from './components/getRunActions';
import LoadingView from '../../../special/LoadingView';
import localization from '../../../../utils/localization';
import {Alert, message, Modal, Row} from 'antd';
import {runPipelineActions, stopRun, terminateRun} from '../../../runs/actions';
import mapResumeFailureReason from '../../../runs/utilities/map-resume-failure-reason';
import roleModel from '../../../../utils/roleModel';
import styles from './Panel.css';

@roleModel.authenticationInfo
@localization.localizedComponent
@runPipelineActions
@inject('runSSH')
@observer
export default class MyActiveRunsPanel extends localization.LocalizedReactComponent {

  static propTypes = {
    panelKey: PropTypes.string,
    activeRuns: PropTypes.object,
    onInitialize: PropTypes.func,
    refresh: PropTypes.func
  };

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
    await checkRequest.fetch();
    let content;
    if (checkRequest.loaded && !checkRequest.value) {
      content = (
        <Alert
          type="error"
          message={PIPELINE_RUN_COMMIT_CHECK_FAILED} />
      );
    }
    Modal.confirm({
      title: warning,
      content,
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => action(),
      okText: actionText,
      cancelText: 'CANCEL',
      width: 450
    });
  };

  pauseRun = async (run) => {
    const hide = message.loading('Pausing...', -1);
    const request = new PausePipeline(run.id);
    await request.send({});
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
    this.props.router.push(`/launch/${run.id}`);
  };

  renderContent = () => {
    let content;
    if (!this.props.activeRuns.loaded && this.props.activeRuns.pending) {
      content = <LoadingView />;
    } else if (this.props.activeRuns.error) {
      content = <Alert type="warning" message={this.props.activeRuns.error} />;
    } else {
      content = [
        <CardsPanel
          key="runs"
          panelKey={this.props.panelKey}
          onClick={this.navigateToRun}
          emptyMessage="There are no active runs"
          actions={
            getRunActions({
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
              openUrl: url => {
                window.open(url, '_blank').focus();
              },
              ssh: async run => {
                const runSSH = this.props.runSSH.getRunSSH(run.id);
                await runSSH.fetchIfNeededOrWait();

                if (runSSH.loaded) {
                  window.open(runSSH.value, '_blank').focus();
                }
                if (runSSH.error) {
                  message.error(runSSH.error);
                }
              }
            })
          }
          cardClassName={run => {
            if (run.initialized && run.serviceUrl) {
              return styles.runServiceCard;
            }
            return undefined;
          }}
          childRenderer={renderRunCard}>
          {this.getActiveRuns()}
        </CardsPanel>,
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
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return (<Alert type="warning" message={this.props.authenticatedUserInfo.error} />);
    }
    return (
      <div className={styles.container}>
        {this.renderContent()}
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
