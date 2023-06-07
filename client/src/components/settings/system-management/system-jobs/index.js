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
import {observable, computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Alert, Button, Dropdown, Icon, message} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import LoadingView from '../../../special/LoadingView';
import SubSettings from '../../sub-settings';
import RunTable, {Columns} from '../../../runs/run-table';
import StatusIcon from '../../../special/run-status-icon';
import UserName from '../../../special/UserName';
import displayDate from '../../../../utils/displayDate';
import SystemJobLog from './system-job-log';
import SystemJobParameters from './system-job-parameters';
import VersionFile from '../../../../models/pipelines/VersionFile';
import styles from './system-jobs.css';

function autoUpdateJobs (state) {
  const {
    runs = []
  } = state || {};
  return runs.some((run) => /^running$/i.test(run.status));
}

function extractScriptParameters (text = '') {
  const [parametersRow] = text
    .split('\n')
    .map(row => row.trim())
    .filter(row => row.startsWith('#sys_job'));
  if (!parametersRow) {
    return undefined;
  }
  const parameterString = parametersRow.replace('#sys_job', '');
  let parameters;
  let error;
  if (!parameterString) {
    return undefined;
  }
  try {
    parameters = JSON.parse(parameterString);
  } catch (e) {
    error = 'JSON validation error in system job parameters from script';
    console.error(e);
  }
  return [error, parameters];
};

@inject('systemJobs')
@observer
class SystemJobs extends React.Component {
  state = {
    job: undefined,
    launchingJobs: [],
    refreshToken: 0,
    launchParameters: undefined,
    jobContentPending: false,
    selectedJobId: undefined
  }

  @observable
  jobScriptParameters = {};

  componentDidMount () {
    const {
      systemJobs
    } = this.props;
    systemJobs.update();
  }

  /**
   * @returns {SystemJob[]}
   */
  get jobs () {
    const {
      systemJobs = {}
    } = this.props;
    const {
      jobs = [],
      loaded
    } = systemJobs;
    if (!loaded) {
      return [];
    }
    return jobs;
  }

  @computed
  get currentJobParameters () {
    const {selectedJobId} = this.state;
    return this.jobScriptParameters[selectedJobId] || {};
  }

  @computed
  get currentJobHasRequiredParameters () {
    return this.currentJobParameters.parameters &&
      this.currentJobParameters.parameters
        .some(parameter => `${parameter.mandatory}` === 'true');
  }

  openJobLog = (job, run) => {
    this.setState({job: {job, run}});
  };

  closeJobLog = () => {
    this.setState({job: undefined});
  };

  openLaunchParametersModal = (job) => {
    this.setState({
      launchParameters: job
    });
  }

  closeLaunchParametersModal = () => {
    this.setState({launchParameters: undefined});
  }

  launchJob = (job, parameters) => {
    const {
      systemJobs
    } = this.props;
    const {
      launchingJobs = [],
      refreshToken = 0
    } = this.state;
    const hide = message.loading(`Launching ${job.identifier}...`, 0);
    this.setState({
      launchingJobs: [...launchingJobs, job.identifier]
    }, async () => {
      try {
        await systemJobs.launchJob(job, parameters);
        this.setState({refreshToken: refreshToken + 1});
      } catch (error) {
        message.error(error.message, 5);
      } finally {
        hide();
        const {
          launchingJobs: currentLaunchingJobs = []
        } = this.state;
        this.setState({
          launchingJobs: currentLaunchingJobs.filter((aJob) => aJob !== job.identifier)
        });
      }
    });
  };

  launchWithParameters = (job, parameters) => {
    this.closeLaunchParametersModal();
    this.launchJob(job, parameters);
  };

  setSelectedJobId = (jobId) => {
    this.setState({selectedJobId: jobId}, () => this.fetchScriptFile(jobId));
  };

  fetchScriptFile = (jobId) => {
    const currentJob = this.jobs.find(job => job.identifier === jobId);
    if (currentJob) {
      if (this.jobScriptParameters[currentJob.path]) {
        return;
      }
      this.setState({scriptContentPending: true}, async () => {
        const request = new VersionFile(
          currentJob.pipelineId,
          currentJob.path,
          currentJob.pipelineVersion
        );
        await request.fetch();
        if (request.error) {
          message.error(request.error, 5);
          return this.setState({scriptContentPending: false});
        }
        const [jsonError, parameters] = extractScriptParameters(atob(request.response || '')) || [];
        if (jsonError) {
          console.error(jsonError, 5);
          return this.setState({scriptContentPending: false});
        }
        this.jobScriptParameters[jobId] = parameters;
        this.setState({scriptContentPending: false});
      });
    }
  };

  /**
   * @param {SystemJob} job
   */
  renderSystemJob = (job) => {
    const {scriptContentPending} = this.state;
    if (scriptContentPending) {
      return <LoadingView />;
    }
    const {router} = this.props;
    const openRunDetails = (run, event) => {
      if (event) {
        event.stopPropagation();
        event.preventDefault();
      }
      if (router) {
        router.push(`/run/${run.id}`);
      }
    };
    const openRunLog = (run, event) => {
      if (event) {
        event.stopPropagation();
        event.preventDefault();
      }
      this.openJobLog(job, run);
    };
    const {
      launchingJobs = [],
      refreshToken
    } = this.state;
    const isLaunching = launchingJobs.includes(job.identifier);
    const onRefresh = () => this.setState({refreshToken: refreshToken + 1});
    const handleLaunchMenu = ({key}) => {
      switch (key) {
        case 'default': this.launchJob(job); break;
        case 'custom':
          this.openLaunchParametersModal(job);
          break;
      }
    };
    const launchMenu = (
      <Menu
        onClick={handleLaunchMenu}
        selectedKeys={[]}
        style={{cursor: 'pointer'}}
      >
        <MenuItem key="custom">
          Launch with parameters
        </MenuItem>
      </Menu>
    );
    return (
      <div className={styles.systemJobContainer}>
        <div
          className={styles.systemJobHeader}
        >
          <span className={styles.systemJobTitle}>{job.identifier}</span>
          <div
            className={styles.systemJobActions}
          >
            <Button
              size="small"
              className={styles.systemJobActionButton}
              onClick={onRefresh}
            >
              REFRESH
            </Button>
            {
              isLaunching || this.currentJobHasRequiredParameters ? (
                <Button
                  type="primary"
                  size="small"
                  disabled={isLaunching}
                  className={styles.systemJobActionButton}
                  onClick={!isLaunching
                    ? () => this.openLaunchParametersModal(job)
                    : undefined
                  }
                >
                  LAUNCH
                </Button>
              ) : (
                <Dropdown.Button
                  type="primary"
                  size="small"
                  style={{marginLeft: 5}}
                  onClick={() => this.launchJob(job)}
                  overlay={launchMenu}
                >
                  LAUNCH
                </Dropdown.Button>
              )
            }
          </div>
        </div>
        <RunTable
          columns={[Columns.run]}
          autoUpdate={{
            enabled: autoUpdateJobs,
            updateToken: refreshToken
          }}
          className={styles.systemJobTableContainer}
          tableClassName={styles.systemJobTable}
          filters={
            {
              pipelineIds: [job.pipelineId],
              tags: {'CP_SYSTEM_JOB': job.identifier}
            }
          }
          onRunClick={(run) => openRunLog(run)}
          runRowClassName={() => 'cp-even-odd-element'}
          customRunRenderer={(run) => (
            <div
              className={styles.systemJobRun}
            >
              <StatusIcon
                className={styles.systemJobStatusIcon}
                run={run}
                small
              />
              <b
                style={{marginLeft: 5}}
              >
                #{run.id}
              </b>
              <UserName
                userName={run.owner}
                showIcon
                style={{marginLeft: 5}}
              />
              <span>
                , launched at <b>{displayDate(run.startDate)}</b>
              </span>
              <div className={styles.systemJobActions}>
                <Button
                  className={styles.openRunButton}
                  size="small"
                  onClick={(event) => openRunLog(run, event)}
                >
                  LOG
                </Button>
                <Button
                  className={styles.openRunButton}
                  size="small"
                  onClick={(event) => openRunDetails(run, event)}
                >
                  DETAILS
                </Button>
              </div>
            </div>
          )}
        />
      </div>
    );
  };

  render () {
    const {
      systemJobs = {}
    } = this.props;
    const {
      loaded,
      pending,
      error
    } = systemJobs;
    const {jobs} = this;
    if (pending && !loaded) {
      return (
        <LoadingView />
      );
    }
    if (!pending && error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    if (!jobs || !jobs.length) {
      return (
        <Alert
          message="System jobs not configured"
          type="info"
        />
      );
    }
    const sections = jobs.map((aJob) => ({
      key: aJob.identifier,
      title: (<span><Icon type="code-o" style={{marginRight: 5}} />{aJob.identifier}</span>),
      name: aJob.identifier,
      render: () => this.renderSystemJob(aJob)
    }));
    const {
      job,
      launchParameters
    } = this.state;
    return (
      <SubSettings
        className={styles.container}
        sections={sections}
        showSectionsSearch
        sectionsSearchPlaceholder="Filter jobs"
        onSectionChange={this.setSelectedJobId}
      >
        <SystemJobLog
          visible={!!job}
          runId={job && job.run ? job.run.id : undefined}
          taskName={job && job.job ? job.job.outputTask : undefined}
          autoUpdate={!!job && !!job.run && /^(running)$/i.test(job.run.status)}
          onClose={this.closeJobLog}
        />
        <SystemJobParameters
          visible={!!launchParameters}
          job={launchParameters}
          onLaunch={this.launchWithParameters}
          onCancel={this.closeLaunchParametersModal}
          parametersFromScript={this.currentJobParameters}
        />
      </SubSettings>
    );
  }
}

SystemJobs.propTypes = {
  router: PropTypes.object
};

export default SystemJobs;
