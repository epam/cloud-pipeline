/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {Icon, message, Modal, Popover} from 'antd';
import {inject, observer} from 'mobx-react';
import roleModel from '../../../../utils/roleModel';
import {canPauseRun, runPipelineActions} from '../../actions';
import getMaintenanceDisabledButton from '../../controls/get-maintenance-mode-disabled-button';
import confirmPause from '../../actions/pause-confirmation';
import PausePipeline from '../../../../models/pipelines/PausePipeline';
import ResumePipeline from '../../../../models/pipelines/ResumePipeline';
import {renderPipelineName} from './utilities';
import localization from '../../../../utils/localization';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

const getColumnFilter = () => {};

class PauseResumeButtonComponent extends localization.LocalizedReactComponent {
  @computed
  get maintenanceMode () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return preferences.systemMaintenanceMode;
    }
    return false;
  }

  pausePipeline = async () => {
    const {
      run,
      reload
    } = this.props;
    if (!run) {
      return;
    }
    const pausePipeline = new PausePipeline(run.id);
    try {
      await pausePipeline.send({});
      if (pausePipeline.error) {
        throw new Error(pausePipeline.error);
      }
      if (typeof reload === 'function') {
        reload();
      }
    } catch (error) {
      message.error(error.message, 5);
    }
  };

  resumePipeline = async () => {
    const {
      run,
      reload
    } = this.props;
    if (!run) {
      return;
    }
    const resumePipeline = new ResumePipeline(run.id);
    try {
      await resumePipeline.send({});
      if (resumePipeline.error) {
        throw new Error(resumePipeline.error);
      }
      if (typeof reload === 'function') {
        reload();
      }
    } catch (error) {
      message.error(error.message, 5);
    }
  };

  showPauseConfirmDialog = async (event) => {
    const {
      run
    } = this.props;
    if (!run) {
      return;
    }
    event.stopPropagation();
    const confirmed = await confirmPause({id: run.id, run});
    if (confirmed) {
      return this.pausePipeline();
    }
  };

  showResumeConfirmDialog = (event) => {
    const {
      run
    } = this.props;
    if (!run) {
      return;
    }
    event.stopPropagation();
    Modal.confirm({
      title: (
        <span>
          {/* eslint-disable-next-line max-len */}
          Do you want to resume {renderPipelineName(run, true, true) || this.localizedString('pipeline')}?
        </span>
      ),
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => this.resumePipeline(),
      okText: 'RESUME',
      cancelText: 'CANCEL'
    });
  };

  render () {
    const {
      preferences,
      run
    } = this.props;
    if (!run) {
      return null;
    }
    if (
      roleModel.executeAllowed(run) &&
      roleModel.isOwner(run) &&
      run.initialized && !(run.nodeCount > 0) &&
      !(run.parentRunId && run.parentRunId > 0) &&
      run.instance && run.instance.spot !== undefined &&
      !run.instance.spot && run.platform !== 'windows'
    ) {
      switch (run.status.toLowerCase()) {
        case 'pausing':
          return (
            <span
              id={`run-${run.id}-pausing`}
            >
              PAUSING
            </span>
          );
        case 'resuming':
          return (
            <span
              id={`run-${run.id}-resuming`}
            >
              RESUMING
            </span>
          );
        case 'running':
          if (canPauseRun(run, preferences)) {
            const buttonId = `run-${run.id}-pause-button`;
            if (this.maintenanceMode) {
              return getMaintenanceDisabledButton('PAUSE', buttonId);
            }
            return (
              <a
                id={buttonId}
                onClick={this.showPauseConfirmDialog}
              >
                PAUSE
              </a>
            );
          }
          break;
        case 'paused':
          const {resumeFailureReason} = run;
          const buttonId = `run-${run.id}-resume-button`;
          if (this.maintenanceMode) {
            return getMaintenanceDisabledButton('RESUME', buttonId);
          }
          return (
            <a
              id={buttonId}
              onClick={this.showResumeConfirmDialog}>
              {
                resumeFailureReason
                  ? (
                    <Popover
                      title={null}
                      placement="left"
                      content={
                        <div style={{maxWidth: '40vw'}}>
                          {resumeFailureReason}
                        </div>
                      }
                    >
                      <Icon
                        type="exclamation-circle-o"
                        className="cp-danger"
                        style={{
                          marginRight: 5
                        }}
                      />
                    </Popover>
                  )
                  : null
              }
              RESUME
            </a>
          );
      }
    }
    return (<div />);
  }
}

const PauseResumeButton = inject(
  'routing', 'pipelines', 'localization', 'dockerRegistries', 'preferences'
)(runPipelineActions(observer(PauseResumeButtonComponent)));

const getColumn = (localized, reload) => ({
  key: 'actionPause',
  className: styles.runRow,
  render: (text, record) => (
    <RunLoadingPlaceholder run={record} empty>
      <PauseResumeButton
        run={record}
        reload={reload}
      />
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
