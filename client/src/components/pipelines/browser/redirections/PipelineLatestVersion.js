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
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import LoadingView from '../../../special/LoadingView';

class PipelineLatestVersion extends React.PureComponent {
  state = {
    error: undefined,
    pipeline: undefined
  };

  componentDidMount () {
    this.navigate();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {
      routeParams = {}
    } = this.props;
    const {
      pipeline: propPipeline
    } = routeParams;
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
      routeParams = {},
      pipelines,
      router
    } = this.props || {};
    const {
      pipeline,
      section,
      subSection
    } = routeParams;
    let queryString = Object
      .entries(location.query || {})
      .map(([key, value]) => `${key}=${value}`)
      .join('&');
    if (queryString) {
      queryString = `?${queryString}`;
    }
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
                  router.push(`/${pipeline}`);
                } else {
                  const {
                    name
                  } = currentVersion;
                  if (!name) {
                    router.push(`/${pipeline}`);
                  } else {
                    router.push(`/${pipeline}/${name}${restUrl}`);
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

export default inject('pipelines')(observer(PipelineLatestVersion));
