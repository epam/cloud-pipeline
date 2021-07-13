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
import parseRunServiceUrl from '../../../../utils/parseRunServiceUrl';

const FETCH_INFO_SEC = 2;

class HaloJobLink extends React.Component {
  state = {
    jobInfo: undefined
  };

  get url () {
    const {jobInfo} = this.state;
    if (jobInfo && jobInfo.initialized) {
      const {serviceUrl} = jobInfo;
      if (serviceUrl) {
        const urls = parseRunServiceUrl(serviceUrl);
        const defaultUrl = urls.find(url => Boolean(url.isDefault)) || urls[0];
        if (defaultUrl) {
          return defaultUrl.url;
        }
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
    this.fetchJobStatus();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.jobId !== this.props.jobId) {
      this.resetJobInfo();
    }
  }

  componentWillUnmount () {
    this.updateJobInfoCallback = undefined;
    this.clearJobStatusTimer();
  }

  resetJobInfo = () => {
    this.setState({
      jobInfo: undefined
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
    const {jobId} = this.props;
    const {jobInfo} = this.state;
    if (jobId && (!jobInfo || !jobInfo.initialized)) {
      const timer = () => {
        this.fetchJobStatusTimer = setTimeout(
          this.fetchJobStatus.bind(this),
          FETCH_INFO_SEC * 1000
        );
      };
      const request = new PipelineRunInfo(jobId);
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
    const {jobId} = this.props;
    const {jobInfo} = this.state;
    if (!jobId || !jobInfo) {
      return (<Icon type="loading" />);
    }
    if (!jobInfo || !jobInfo.initialized || !this.url) {
      return (
        <span>
          Wait for HALO instance <Link to={`run/${jobId}`}>#{jobId}</Link> to initialize
        </span>
      );
    }
    return (
      <span>
        Open HALO desktop:
        <a
          href={this.url}
          target="_blank"
          style={{marginLeft: 5}}
        >
          Download remote desktop shortcut
        </a>
      </span>
    );
  }
}

HaloJobLink.propTypes = {
  jobId: PropTypes.oneOfType([PropTypes.number, PropTypes.string])
};

export default HaloJobLink;
