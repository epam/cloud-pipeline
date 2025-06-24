/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Spin} from 'antd';
import {computed, observable} from 'mobx';
import {Observer, observer} from 'mobx-react';
import {Link} from 'react-router';
import StatusIcon from '../../../special/run-status-icon';
import PipelineRunInfo from '../../../../models/pipelines/PipelineRunInfo';
import RunStatuses, {getRunStatus} from '../../../special/run-status-icon/run-statuses';

const PREPARING_STATUSES = [
  RunStatuses.pausing,
  RunStatuses.pulling,
  RunStatuses.queued,
  RunStatuses.nodePending,
  RunStatuses.resuming,
  RunStatuses.scheduled
];

const RUNNING_STATUSES = [
  RunStatuses.running,
  RunStatuses.paused
];

const TIMEOUTS = { // seconds
  preparing: 5,
  running: 15
};

@observer
export default class RunStatusMessage extends React.Component {
  state ={
    run: undefined
  }

  @observable
  _runRequest;

  _timeout;

  componentDidMount () {
    this.setStateFromProps();
  }

  componentDidUpdate (prevProps) {
    if (this.props.run?.id !== prevProps.run?.id) {
      this.setStateFromProps();
    }
  }

  componentWillUnmount () {
    clearTimeout(this._timeout);
  }

  @computed
  get run () {
    if (!this._runRequest) {
      return this.state.run;
    }
    return this._runRequest.value;
  }

  setStateFromProps = () => {
    this.setState({run: this.props.run}, () => {
      this.initializeRunPolling();
    });
  };

  initializeRunPolling = () => {
    const getStatusType = status => {
      if (PREPARING_STATUSES.find(s => s === status)) {
        return 'preparing';
      }
      if (RUNNING_STATUSES.find(s => s === status)) {
        return 'running';
      }
      return 'stopped';
    };
    const fetchRun = async () => {
      const {run} = this.state;
      if (!run) {
        return;
      }
      const request = new PipelineRunInfo(run.id);
      await request.fetch();
      this._runRequest = request;
      const statusType = getStatusType(getRunStatus(request.value));
      if (statusType === 'stopped') {
        return;
      }
      this._timeout = setTimeout(() => {
        fetchRun();
      }, (TIMEOUTS[statusType] || 5) * 1000);
    };
    fetchRun();
  };

  render () {
    if (!this.run) {
      return <Spin spinning />;
    }
    const {aclClass = '', name, taskName} = this.run;
    const runType = aclClass.toLowerCase();
    const runName = runType === 'pipeline'
      ? `${name || taskName}-${this.run.id}`
      : name || taskName;
    return (
      <div style={{display: 'flex', alignItems: 'center', gap: '5px'}}>
        <span style={{textTransform: 'capitalize'}}>{runType}</span>
        <Observer>
          {() => (
            <StatusIcon
              run={this.run}
              small
            />
          )}
        </Observer>
        <Link to={`run/${this.run.id}`}>
          {runName}
        </Link>
        was successfully launched!
      </div>
    );
  }
}

RunStatusMessage.propTypes = {
  run: PropTypes.object
};
