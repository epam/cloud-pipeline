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
import {observer} from 'mobx-react';
import LoadingView from '../../../special/LoadingView';
import localization from '../../../../utils/localization';
import {Alert, message, Modal, Row} from 'antd';
import CardsPanel from './components/CardsPanel';
import StopPipeline from '../../../../models/pipelines/StopPipeline';
import PausePipeline from '../../../../models/pipelines/PausePipeline';
import ResumePipeline from '../../../../models/pipelines/ResumePipeline';
import renderRunCard from './components/renderRunCard';
import getRunActions from './components/getRunActions';
import roleModel from '../../../../utils/roleModel';
import moment from 'moment-timezone';
import styles from './Panel.css';

@roleModel.authenticationInfo
@localization.localizedComponent
@observer
export default class RecentlyCompletedRunsPanel extends localization.LocalizedReactComponent {

  static propTypes = {
    panelKey: PropTypes.string,
    completedRuns: PropTypes.object,
    onInitialize: PropTypes.func
  };

  get usesCompletedRuns () {
    return true;
  }

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

  stopRun = async (run) => {
    const hide = message.loading('Stopping...', -1);
    const request = new StopPipeline(run.id);
    await request.send({
      endDate: moment().format('YYYY-MM-DD HH:mm:ss.SSS'),
      status: 'STOPPED'
    });
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      this.props.refresh && await this.props.refresh();
      hide();
    }
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
    if (!this.props.completedRuns.loaded && this.props.completedRuns.pending) {
      content = <LoadingView />;
    } else if (this.props.completedRuns.error) {
      content = <Alert type="warning" message={this.props.completedRuns.error} />;
    } else {
      content = [
        <Row key="runs" style={{flex: 1, overflowY: 'auto'}}>
          <CardsPanel
            key="runs"
            panelKey={this.props.panelKey}
            cardClassName={styles.runCard}
            onClick={this.navigateToRun}
            emptyMessage="There are no completed runs yet."
            actions={
              getRunActions({
                pause: run => this.confirm(
                  `Are you sure you want to pause run ${run.podId}?`,
                  'PAUSE',
                  () => this.pauseRun(run)
                ),
                resume: run => this.confirm(
                  `Are you sure you want to resume run ${run.podId}?`,
                  'RESUME',
                  () => this.resumeRun(run)
                ),
                stop: run => this.confirm(
                  `Are you sure you want to stop run ${run.podId}?`,
                  'STOP',
                  () => this.stopRun(run)
                ),
                run: this.reRun
              })
            }
            childRenderer={renderRunCard}>
            {
              this.props.completedRuns.loaded
                ? (this.props.completedRuns.value || [])
                  .map(r => r)
                  .filter(r => ['SUCCESS', 'STOPPED', 'FAILURE'].indexOf(r.status) >= 0)
                : []
            }
          </CardsPanel>
        </Row>,
        <Row
          key="explore other"
          type="flex"
          justify="center"
          style={{margin: 10}}>
          <span
            style={{
              fontStyle: 'italic'
            }}>
            Explore all <Link to="/runs/completed">completed runs</Link>
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
