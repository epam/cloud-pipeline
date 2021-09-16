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
import {withRouter} from 'react-router-dom';
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import LoadingView from '../../../special/LoadingView';

class PipelineLatestVersion extends React.Component {
  state = {
    error: undefined,
    pipeline: undefined
  };

  componentDidMount () {
    this.navigate();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {
      match = {}
    } = this.props;
    const {
      pipeline: propPipeline
    } = match.params;
    const {
      pipeline
    } = this.state;
    if (+propPipeline !== +pipeline) {
      this.navigate();
    }
  }

  navigate = () => {
    const {
      location = {},
      match = {},
      pipelines,
      history
    } = this.props || {};
    const {
      pipeline,
      section,
      subSection
    } = match.params;
    const queryString = location.search || '';
    let subPath = [section, subSection].filter(Boolean).join('/');
    if (subPath) {
      subPath = `/${subPath}`;
    }
    const restUrl = `${subPath}${queryString}`;
    let error;
    if (!pipeline) {
      error = 'Unknown pipeline';
    }
    this.setState({
      pipeline,
      error
    }, () => {
      if (pipeline) {
        const pipelineRequest = pipelines.getPipeline(pipeline);
        pipelineRequest
          .fetch()
          .then(() => {
            if (pipeline === this.state.pipeline) {
              if (pipelineRequest.error || !pipelineRequest.loaded) {
                this.setState({
                  error: pipelineRequest.error
                });
              } else {
                const result = pipelineRequest.value || {};
                const {
                  currentVersion
                } = result;
                if (!currentVersion) {
                  history.push(`/${pipeline}`);
                } else {
                  const {
                    name
                  } = currentVersion;
                  if (!name) {
                    history.push(`/${pipeline}`);
                  } else {
                    history.push(`/${pipeline}/${name}${restUrl}`);
                  }
                }
              }
            }
          });
      }
    });
  };

  render () {
    const {
      error
    } = this.state;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    return (
      <LoadingView />
    );
  }
}

export default inject('pipelines')(withRouter(observer(PipelineLatestVersion)));
