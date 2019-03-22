/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {Component} from 'react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Link} from 'react-router';
import dockerRegistries from '../../../models/tools/DockerRegistriesTree';
import {inject, observer} from 'mobx-react/index';
import connect from '../../../utils/connect';
import LoadingView from '../../special/LoadingView';

const findGroupByNameSelector = (name) => (group) => {
  return group.name.toLowerCase() === name.toLowerCase();
};
const findGroupByName = (groups, name) => {
  return groups.filter(findGroupByNameSelector(name))[0] || null;
};

@connect({dockerRegistries})
@inject(({dockerRegistries}) => {
  return {
    dockerRegistries
  };
})
@observer
export default class DockerImageLink extends Component {

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return (this.props.dockerRegistries.value.registries || []).map(r => r);
    }
    return [];
  }

  @computed
  get namesFromPath () {
    if (!this.props.path ||
      !this.props.path.includes('/') ||
      (this.props.path.match(/\//g) || []).length < 2) {
      return null;
    }
    let [registryPath, groupName, toolName] = this.props.path.split('/');

    if (toolName.includes(':')) {
      toolName = toolName.split(':').shift();
    }
    return {registryPath, groupName, toolName};
  }

  @computed
  get currentRegistry () {
    if (!this.namesFromPath) {
      return null;
    }
    const {registryPath} = this.namesFromPath;

    return this.registries.filter(r => r.path === registryPath).shift() || null;
  }

  @computed
  get currentGroup () {
    if (!this.namesFromPath) {
      return null;
    }
    const {groupName} = this.namesFromPath;

    return (this.currentRegistry && findGroupByName(this.currentRegistry.groups, groupName)) ||
      null;
  }

  @computed
  get currentTools () {
    return (this.currentGroup && this.currentGroup.tools) || [];
  }

  @computed
  get currentToolId () {
    if (!this.namesFromPath) {
      return null;
    }
    const {groupName, toolName} = this.namesFromPath;
    const [toolId] = this.currentTools
      .filter(tool =>
        `${tool.image.toLowerCase()}` === `${groupName.toLowerCase()}/${toolName.toLowerCase()}`)
      .map(tool => tool.id);

    return toolId || null;
  }

  render () {
    if (this.props.dockerRegistries.pending && !this.props.dockerRegistries.loaded) {
      return <LoadingView />;
    }
    if (this.props.dockerRegistries.error || !this.namesFromPath || !this.currentToolId) {
      return (<span>{ this.props.path }</span>);
    }

    return (
      <Link to={`/tool/${this.currentToolId}`}>
        <span>{ this.props.path }</span>
      </Link>
    );
  }
}

DockerImageLink.propTypes = {
  path: PropTypes.string
};
