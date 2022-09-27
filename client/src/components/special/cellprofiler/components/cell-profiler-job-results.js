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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Alert} from 'antd';
import {inject, observer} from 'mobx-react';
import {isObservableArray} from 'mobx';
import {Link} from 'react-router';
import {getBatchJobInfo, getSpecification} from '../model/analysis/batch';
import LoadingView from '../../LoadingView';
import StatusIcon from '../../run-status-icon';
import displayDate from '../../../../utils/displayDate';
import Duration from './components/duration';
import UserName from '../../UserName';
import {AnalysisOutputWithDownload} from './analysis-output';
import Collapse from './collapse';
import styles from './cell-profiler.css';

const REFETCH_JOB_TIMEOUT_MS = 1000 * 5;

function parseInputs (inputs = []) {
  if (!inputs) {
    return {
      files: [],
      zPlanes: []
    };
  }
  if (Array.isArray(inputs) || isObservableArray(inputs)) {
    return {
      files: inputs,
      zPlanes: []
    };
  }
  return inputs;
}

class CellProfilerJobResults extends React.PureComponent {
  state = {
    pending: false,
    error: undefined,
    job: undefined,
    specification: undefined
  };
  componentDidMount () {
    this.fetchJob(true);
  }
  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.jobId !== this.props.jobId) {
      this.fetchJob(true);
    }
  }
  componentWillUnmount () {
    this.unmounted = true;
    clearTimeout(this.jobReFetchTimeout);
  }

  fetchJob = (clear = false) => {
    clearTimeout(this.jobReFetchTimeout);
    const {
      jobId
    } = this.props;
    if (jobId) {
      const pendingState = {
        pending: true
      };
      if (clear) {
        pendingState.job = undefined;
        pendingState.specification = undefined;
      }
      this.setState(pendingState, async () => {
        const state = {
          pending: false,
          error: undefined
        };
        try {
          const job = await getBatchJobInfo(jobId);
          if (jobId !== this.props.jobId) {
            return;
          }
          if (!job) {
            throw new Error('Batch analysis job not found');
          }
          state.job = job;
          if (/^(running|pausing|pulling|queued|resuming)$/i.test(job.status)) {
            this.jobReFetchTimeout = setTimeout(() => this.fetchJob(), REFETCH_JOB_TIMEOUT_MS);
          }
        } catch (error) {
          state.error = error.message;
          state.job = undefined;
        } finally {
          if (!this.unmounted) {
            this.setState(
              state,
              () => this.fetchJobSpecification()
            );
          }
        }
      });
    } else {
      this.setState({
        job: undefined,
        pending: false,
        error: undefined
      });
    }
  };
  fetchJobSpecification = (force = false) => {
    const {
      specification,
      job
    } = this.state;
    if (job && (force || !specification)) {
      getSpecification(job)
        .then(spec => {
          this.setState({
            specification: spec
          });
        })
        .catch((error) => console.warn(error.message));
    }
  };
  renderJobTitle = () => {
    const {
      job
    } = this.state;
    if (!job) {
      return null;
    }
    let name = 'Batch results';
    let details;
    const input = job.input && job.input.path
      ? job.input.path.split('/').pop()
      : undefined;
    if (job.alias && input) {
      name = job.alias;
      details = input;
    } else if (job.alias) {
      name = job.alias;
    } else if (input) {
      name = input;
    }
    return (
      <div
        className={styles.title}
      >
        <StatusIcon run={job.job} />
        <b>{name}</b>
        {
          details && (<span>{'-'}</span>)
        }
        {
          details && (
            <span>
              {details}
            </span>
          )
        }
      </div>
    );
  };
  renderJobInfo = () => {
    const {
      job,
      specification
    } = this.state;
    if (!job) {
      return null;
    }
    const {
      startDate,
      endDate,
      owner,
      input,
      outputFolder
    } = job;
    const renderInfo = (key, value) => {
      if (value) {
        return (
          <div
            className={styles.cellProfilerJobInfoRow}
          >
            <b>{key}:</b>
            <span className={styles.value}>{value}</span>
          </div>
        );
      }
      return null;
    };
    const getStorageById = (id) => {
      const {
        dataStorages
      } = this.props;
      if (dataStorages.loaded) {
        return (dataStorages.value || []).find(d => Number(d.id) === Number(id));
      }
      return undefined;
    };
    const renderLocation = (key, value, folder = false) => {
      if (!value) {
        return null;
      }
      const {
        storageId,
        path
      } = value;
      const parentFolder = folder
        ? path
        : (path || '').split('/').slice(0, -1).join('/');
      let link = `storage/${storageId}`;
      const query = {};
      if (parentFolder && parentFolder.length) {
        query.path = parentFolder;
      }
      const storage = getStorageById(storageId);
      let displayPath = path;
      if (storage) {
        displayPath = storage.pathMask.concat(storage.delimiter || '/').concat(path);
      }
      const file = (path || '').split('/').pop();
      if (/\.hcs$/i.test(file)) {
        query.preview = file;
      }
      if (Object.keys(query).length > 0) {
        link = link
          .concat(`?${Object.entries(query).map(([key, value]) => `${key}=${value}`).join('&')}`);
      }
      return renderInfo(
        key,
        <Link
          to={link}
          target="_blank"
        >
          {displayPath}
        </Link>
      );
    };
    const renderSpecificationInfo = () => {
      if (!specification) {
        return null;
      }
      const {
        inputs
      } = specification;
      const {
        files = [],
        zPlanes = []
      } = parseInputs(inputs);
      const wellsCount = new Set(files.map(o => `${o.x}|${o.y}`)).size;
      const fieldsCount = new Set(files.map(o => o.fieldId)).size;
      const timePointsCount = new Set(files.map(o => o.timepoint)).size;
      const zPlanesCount = new Set(files.map(o => o.z)).size;
      const mergeZPlanes = zPlanes.length > 0;
      const plural = (count, label) => `${count} ${label}${count === 1 ? '' : 's'}`;
      return renderInfo(
        'Selection',
        (
          <span>
            {plural(wellsCount, 'well')}
            {', '}
            {plural(fieldsCount, 'field')}
            {', '}
            {plural(timePointsCount, 'time point')}
            {', '}
            {plural(zPlanesCount, 'z-plane')}
            {
              mergeZPlanes && ` (projection)`
            }
          </span>
        )
      );
    };
    return (
      <Collapse header="Details">
        {renderInfo('Owner', (<UserName userName={owner} />))}
        {renderInfo('Started', displayDate(startDate, 'D MMMM, YYYY, HH:mm'))}
        {renderInfo('Duration', (<Duration startDate={startDate} endDate={endDate} />))}
        {renderInfo('Job ID', (<Link to={`run/${job.id}`} target="_blank">#{job.id}</Link>))}
        {renderLocation('HCS Image', input)}
        {renderLocation('Output folder', outputFolder, true)}
        {renderSpecificationInfo()}
      </Collapse>
    );
  };
  renderJobOutput = () => {
    const {
      job
    } = this.state;
    if (!job) {
      return null;
    }
    const {
      status,
      outputFolder: output,
      input
    } = job;
    if (!/^(success)$/i.test(status) || !output) {
      return null;
    }
    const fileName = input && input.path
      ? input.path.split('/').pop()
      : undefined;
    return (
      <AnalysisOutputWithDownload
        className={styles.cellProfilerJobResultsOutput}
        storageId={output.storageId}
        path={(output.path || '').concat('/Results.csv')}
        downloadPath={(output.path || '').concat('/Results.xlsx')}
        input={fileName}
        analysisDate={job.startDate}
        analysisName={job.alias || job.pipelineName}
      />
    );
  };
  render () {
    const {
      className,
      style,
      jobId
    } = this.props;
    if (!jobId) {
      return null;
    }
    const {
      error,
      pending,
      job
    } = this.state;
    return (
      <div
        className={
          classNames(
            className,
            styles.cellProfilerJobResults,
            'cp-panel',
            'cp-panel-borderless'
          )
        }
        style={style}
      >
        {
          pending && !job && (
            <LoadingView />
          )
        }
        {
          error && (
            <Alert
              message={error}
              type="error"
            />
          )
        }
        {this.renderJobTitle()}
        {this.renderJobInfo()}
        {this.renderJobOutput()}
      </div>
    );
  }
}

CellProfilerJobResults.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  jobId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default inject('dataStorages')(observer(CellProfilerJobResults));
