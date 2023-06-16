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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import LoadToolInfo from '../../../../models/tools/LoadToolInfo';

@inject('dockerRegistries')
@observer
export default class CudaWarning extends React.Component {
  state = {
    pending: false,
    versionInfo: undefined
  }

  componentDidMount () {
    this.fetchToolInfo();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.docker !== this.props.docker) {
      this.fetchToolInfo();
    }
  }

  @computed
  get tools () {
    const {dockerRegistries} = this.props;
    const result = [];
    if (dockerRegistries.loaded) {
      const {registries = []} = dockerRegistries.value || {};
      for (let r = 0; r < registries.length; r++) {
        const registry = registries[r];
        const {groups = []} = registry;
        for (let g = 0; g < groups.length; g++) {
          const group = groups[g];
          const {tools: groupTools = []} = group;
          for (let t = 0; t < groupTools.length; t++) {
            const tool = groupTools[t];
            if (!tool.link) {
              result.push({
                ...tool,
                dockerImage: `${registry.path}/${tool.image}`.toLowerCase(),
                registry,
                group,
                name: tool.image.split('/').pop()
              });
            }
          }
        }
      }
    }
    return result;
  }

  get cudaAvailable () {
    const {versionInfo} = this.state;
    const {cudaAvailable} = (versionInfo || {}).scanResult || {};
    return cudaAvailable;
  }

  get showWarning () {
    const {gpuEnabledRun = false} = this.props;
    if (this.cudaAvailable === undefined) {
      return false;
    }
    return gpuEnabledRun !== this.cudaAvailable;
  }

  getToolId = (docker) => {
    const [r, g, iv] = docker.split('/');
    const image = iv.split(':').slice(0, -1).join(':');
    const dockerImage = [r, g, image].join('/').toLowerCase();
    const currentTool = this.tools
      .find(tool => tool.dockerImage === dockerImage);
    if (currentTool) {
      return currentTool.id;
    }
    return undefined;
  };

  fetchToolInfo = () => {
    const {docker} = this.props;
    const toolId = this.getToolId(docker);
    if (!toolId) {
      return null;
    }
    this.setState({pending: true}, async () => {
      const request = new LoadToolInfo(toolId);
      await request.fetch();
      let versionInfo;
      if (request.loaded && request.value) {
        const {
          versions = []
        } = request.value || {};
        const [, , iv] = docker.split('/');
        const [, v] = iv.split(':');
        versionInfo = versions.find(version => version.version === v);
      }
      this.setState({
        pending: false,
        versionInfo
      });
    });
  };

  render () {
    const {versionInfo} = this.state;
    const {
      className,
      showIcon,
      style,
      gpuEnabledRun,
      instanceType
    } = this.props;
    if (!versionInfo || !this.showWarning) {
      return null;
    }
    const getMessage = () => {
      if (gpuEnabledRun && !this.cudaAvailable) {
        return (
          // eslint-disable-next-line max-len
          <p>You are going to start a tool <i>without a CUDA toolkit</i>, using a <i>GPU-enabled node ({instanceType})</i>. No CUDA environment will be available, but the compute cost will be high.
          </p>
        );
      }
      if (!gpuEnabledRun && this.cudaAvailable) {
        return (
          // eslint-disable-next-line max-len
          <p>You are going to start a tool <i>with a CUDA toolkit installed</i>, but using a <i>CPU-only node ({instanceType})</i>. No CUDA/Nvidia environment will be available.
          </p>
        );
      }
      return '';
    };
    return (
      <Alert
        type="warning"
        className={className}
        style={style}
        showIcon={showIcon}
        message={getMessage()}
      />
    );
  }
}

CudaWarning.propTypes = {
  className: PropTypes.string,
  showIcon: PropTypes.bool,
  style: PropTypes.object,
  docker: PropTypes.string,
  gpuEnabledRun: PropTypes.bool,
  instanceType: PropTypes.string
};
