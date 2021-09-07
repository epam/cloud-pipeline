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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {
  message,
  Popover
} from 'antd';
import ToolsSelector from './tools-selector';
import OpenToolInfo from './open-tool-info';
import FileTools from './file-tools';
import fetchActiveJobs from '../open-in-halo/fetch-active-jobs';
import {PipelineRunner} from '../../../../models/pipelines/PipelineRunner';
import getToolLaunchingOptions
  from '../../../pipelines/launch/utilities/get-tool-launching-options';
import styles from './open-in-tool.css';

const fileToolsRequest = new FileTools();

@inject('dockerRegistries', 'awsRegions', 'preferences', 'dataStorages')
@inject(() => ({
  fileTools: fileToolsRequest
}))
@observer
class OpenInToolAction extends React.Component {
  state = {
    modalVisible: false,
    activeJobsFetching: false,
    activeTool: undefined,
    activeJob: undefined
  }

  componentDidMount () {
    const {fileTools} = this.props;
    fileTools && fileTools.fetch();
  }

  get fileExtension () {
    const {file: filePath} = this.props;
    if (filePath) {
      const fileParts = filePath.split('/').pop().split('.');
      const extension = fileParts.length > 1 ? fileParts.pop() : undefined;
      return extension;
    }
    return undefined;
  }

  @computed
  get fileTools () {
    const {fileTools} = this.props;
    if (fileTools.loaded) {
      return fileTools.tools.map(t => t);
    }
    return undefined;
  }

  @computed
  get tools () {
    const {dockerRegistries} = this.props;
    if (dockerRegistries.loaded) {
      const result = [];
      const {registries = []} = dockerRegistries.value;
      for (let registry of registries) {
        const {groups = []} = registry;
        for (let group of groups) {
          const {tools = []} = group;
          result.push(
            ...(tools.map(tool => ({
              ...tool,
              group
            })))
          );
        }
      }
      return result;
    }
    return [];
  }

  @computed
  get filteredFileTools () {
    if (this.fileTools) {
      return this.fileTools
        .filter(tool => tool.openInFiles.includes(this.fileExtension))
        .map(tool => this.tools.find(t => t.id === tool.toolId));
    }
    return [];
  }

  @computed
  get activeToolTemplate () {
    const {activeTool} = this.state;
    if (!activeTool) {
      return undefined;
    }
    const tool = this.fileTools.find(tool => tool.toolId === activeTool.id);
    return (tool || {}).template;
  }

  @computed
  get storage () {
    const {storageId, dataStorages} = this.props;
    if (storageId && dataStorages.loaded) {
      return (dataStorages.value || []).find(s => +(s.id) === +storageId);
    }
    return undefined;
  }

  getToolById = (id) => {
    return (this.tools || []).find(tool => tool.id === id);
  };

  fetchJobs = () => {
    const {activeTool} = this.state;
    if (!activeTool) {
      return;
    }
    this.setState({
      activeJobsFetching: true,
      activeJob: undefined
    }, () => {
      fetchActiveJobs()
        .then(jobs => {
          const dockerImage = activeTool
            ? new RegExp(`^${activeTool.registry}/${activeTool.image}(:|$)`, 'i')
            : undefined;
          const job = jobs.find(j => dockerImage.test(j.dockerImage));
          if (job) {
            this.setState({
              activeJobsFetching: false,
              activeJob: job
            });
          } else {
            this.setState({
              activeJobsFetching: false,
              activeJob: undefined
            });
          }
        });
    });
  };

  launch = () => {
    const {activeTool} = this.state;
    if (activeTool) {
      const hide = message.loading('Launching...', 0);
      const request = new PipelineRunner();
      getToolLaunchingOptions(this.props, activeTool)
        .then((launchPayload) => {
          return request.send({...launchPayload, force: true});
        })
        .then(() => {
          if (request.error) {
            throw new Error(request.error);
          } else if (request.loaded) {
            const run = request.value;
            return Promise.resolve(run);
          }
        })
        .catch(e => {
          message.error(e.message, 5);
          return Promise.resolve();
        })
        .then((run) => {
          hide();
          this.setState({activeJob: run, activeJobIsService: false});
        });
    }
  };

  modalVisibilityChanged = visible => {
    if (visible) {
      this.openModal();
    } else {
      this.closeModal();
    }
  };

  openModal = (toolId) => {
    this.setState({
      modalVisible: true,
      activeJobsFetching: true,
      activeJob: undefined
    }, this.fetchJobs);
  };

  closeModal = () => {
    this.setState({
      modalVisible: false,
      activeTool: undefined
    });
  };

  onSelectTool = toolId => {
    const tool = this.getToolById(toolId);
    if (tool) {
      this.setState({activeTool: tool}, () => this.openModal());
    }
  };

  renderToolInfo = () => {
    const {
      activeJob,
      activeJobsFetching,
      activeTool
    } = this.state;
    const {file} = this.props;
    return (
      <OpenToolInfo
        template={this.activeToolTemplate}
        activeJob={activeJob}
        activeJobsFetching={activeJobsFetching}
        file={file}
        storage={this.storage}
        tool={activeTool}
        onLaunchClick={this.launch}
      />
    );
  };

  render () {
    const {
      className,
      style,
      titleStyle
    } = this.props;
    const {modalVisible} = this.state;
    if (this.filteredFileTools.length === 0) {
      return null;
    }
    return (
      <Popover
        onVisibleChange={this.modalVisibilityChanged}
        visible={modalVisible}
        trigger={['click']}
        title={false}
        content={this.renderToolInfo()}
        placement="left"
      >
        <ToolsSelector
          className={classNames(styles.link, className)}
          style={style}
          titleStyle={titleStyle}
          onSelectTool={this.onSelectTool}
          tools={this.filteredFileTools}
        />
      </Popover>
    );
  }
}

OpenInToolAction.propTypes = {
  file: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  className: PropTypes.string,
  style: PropTypes.object,
  titleStyle: PropTypes.object
};

export default OpenInToolAction;
