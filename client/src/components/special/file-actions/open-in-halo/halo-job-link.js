/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Link} from 'react-router';
import {Icon} from 'antd';
import PipelineRunInfo from '../../../../models/pipelines/PipelineRunInfo';
import MultizoneUrl from '../../multizone-url';
import {parseRunServiceUrlConfiguration} from '../../../../utils/multizone';

const FETCH_INFO_SEC = 2;

class HaloJobLink extends React.Component {
  state = {
    jobInfo: undefined
  };

  get url () {
    const {job} = this.props;
    const {jobInfo} = this.state;
    if (job && jobInfo && (job.isService || jobInfo.initialized)) {
      const {serviceUrl} = jobInfo;
      if (serviceUrl) {
        const urls = parseRunServiceUrlConfiguration(serviceUrl);
        return urls.find(url => Boolean(url.isDefault)) || urls[0];
      }
    }
    return undefined;
  }

  componentDidMount () {
    this.updateJobInfoCallback = (jobInfo, cb) => {
      this.setState({
        jobInfo
      }, () => cb && cb());
    };
    this.resetJobInfo();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.job !== this.props.job) {
      this.resetJobInfo();
    }
  }

  componentWillUnmount () {
    this.updateJobInfoCallback = undefined;
    this.clearJobStatusTimer();
  }

  resetJobInfo = () => {
    this.setState({
      jobInfo: this.props.job ? {...this.props.job} : undefined
    }, this.fetchJobStatus);
  };

  clearJobStatusTimer = () => {
    if (this.fetchJobStatusTimer) {
      clearTimeout(this.fetchJobStatusTimer);
      this.fetchJobStatusTimer = null;
    }
  };

  fetchJobStatus = () => {
    this.clearJobStatusTimer();
    const {job} = this.props;
    const {jobInfo} = this.state;
    if (job && (!jobInfo || (!job.isService && !jobInfo.initialized))) {
      const timer = () => {
        this.fetchJobStatusTimer = setTimeout(
          this.fetchJobStatus.bind(this),
          FETCH_INFO_SEC * 1000
        );
      };
      const request = new PipelineRunInfo(job.id);
      request
        .fetch()
        .then(() => {
          if (request.loaded) {
            const jobInfo = request.value;
            this.updateJobInfoCallback &&
            this.updateJobInfoCallback(jobInfo, () => {
              if (!jobInfo.initialized) {
                timer();
              }
            });
          } else {
            throw new Error(request.error || 'Error fetching job info');
          }
        })
        .catch(() => {
          this.updateJobInfoCallback &&
          this.updateJobInfoCallback(undefined, () => {
            timer();
          });
        });
    }
  };

  render () {
    const {job} = this.props;
    const {jobInfo} = this.state;
    if (!job || !jobInfo) {
      return (<Icon type="loading" />);
    }
    if ((!job.isService && !jobInfo.initialized) || !this.url) {
      return (
        <span>
          Wait for HALO instance <Link to={`run/${job.id}`}>#{job.id}</Link> to initialize
        </span>
      );
    }
    return (
      <span>
        Open HALO desktop:
        <MultizoneUrl
          style={{
            display: 'inline-flex',
            marginLeft: 5
          }}
          configuration={this.url.url}
          dropDownIconStyle={{marginTop: 2}}
        >
          Download remote desktop shortcut
        </MultizoneUrl>
      </span>
    );
  }
}

HaloJobLink.propTypes = {
  job: PropTypes.object
};

export default HaloJobLink;
