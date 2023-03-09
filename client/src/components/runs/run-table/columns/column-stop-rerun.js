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
import styles from './run-table-columns.css';
import roleModel from '../../../../utils/roleModel';
import {
  canStopRun,
  openReRunForm,
  runPipelineActions,
  stopRun,
  terminateRun
} from '../../actions';
import localization from '../../../../utils/localization';
import RunLoadingPlaceholder from './run-loading-placeholder';
import {inject, observer} from 'mobx-react';

const getColumnFilter = () => {};

class StopReRunButtonComponent extends localization.LocalizedReactComponent {
  showTerminateConfirmDialog = (event) => {
    const {
      run,
      reload
    } = this.props;
    if (event) {
      event.stopPropagation();
    }
    if (!run) {
      return;
    }
    return terminateRun(this, reload || (() => {}))(run);
  };

  showStopConfirmDialog = (event) => {
    const {
      run,
      reload
    } = this.props;
    if (event) {
      event.stopPropagation();
    }
    if (!run) {
      return;
    }
    return stopRun(this, reload || (() => {}))(run);
  };

  reRunPipeline = (event) => {
    const {
      run
    } = this.props;
    if (!run) {
      return;
    }
    if (event) {
      event.stopPropagation();
    }
    openReRunForm(run, this.props);
  };

  render () {
    const {
      run
    } = this.props;
    if (!run) {
      return null;
    }
    const isPipeline = !!run.version && !run.pipelineId;
    switch ((run.status || '').toLowerCase()) {
      case 'paused':
        if (roleModel.executeAllowed(run) && roleModel.isOwner(run)) {
          return (
            <a
              id={`run-${run.id}-terminate-button`}
              className="cp-danger"
              onClick={this.showTerminateConfirmDialog}
            >
              TERMINATE
            </a>
          );
        }
        break;
      case 'running':
      case 'pausing':
      case 'resuming':
        if (
          (
            roleModel.executeAllowed(run) ||
            run.sshPassword
          ) &&
          (
            roleModel.isOwner(run) ||
            run.sshPassword
          ) &&
          canStopRun(run)
        ) {
          return (
            <a
              id={`run-${run.id}-stop-button`}
              className="cp-danger"
              onClick={this.showStopConfirmDialog}
            >
              STOP
            </a>
          );
        }
        if (run.commitStatus && run.commitStatus.toLowerCase() === 'committing') {
          return (
            <span
              style={{fontStyle: 'italic'}}
            >
              COMMITTING
            </span>
          );
        }
        break;
      case 'stopped':
      case 'failure':
      case 'success':
        if (roleModel.executeAllowed(run) && !isPipeline) {
          return (
            <a
              id={`run-${run.id}-rerun-button`}
              onClick={this.reRunPipeline}
            >
              RERUN
            </a>
          );
        }
    }
    return (<div />);
  }
}

const StopReRunButton = inject(
  'routing', 'pipelines', 'localization', 'dockerRegistries', 'preferences'
)(runPipelineActions(observer(StopReRunButtonComponent)));

const getColumn = (localization, reload) => ({
  key: 'actionRerunStop',
  className: styles.runRow,
  render: (run) => (
    <RunLoadingPlaceholder run={run} empty>
      <StopReRunButton
        run={run}
        reload={reload}
      />
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
