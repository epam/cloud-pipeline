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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Alert,
  Icon,
  Select
} from 'antd';
import DockerImageDetails from '../../../../../../../../cluster/hot-node-pool/docker-image-details';

@inject('dockerRegistries')
@observer
class DockerImageSelector extends React.Component {
  state = {
    search: undefined
  };

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
          if (groupTools.length > 0) {
            const tools = {
              label: (
                <span>
                  {registry.description || registry.path}
                  <Icon type="right" />
                  {group.name}
                </span>
              ),
              key: `${registry.path}/${group.name}`,
              tools: []
            };
            result.push(tools);
            for (let t = 0; t < groupTools.length; t++) {
              const tool = groupTools[t];
              if (!/^windows$/i.test(tool.platform)) {
                tools.tools.push({
                  ...tool,
                  dockerImage: `${registry.path}/${tool.image}`,
                  registry,
                  group,
                  name: tool.image.split('/').pop()
                });
              }
            }
          }
        }
      }
    }
    return result;
  }

  get selectedDockerImage () {
    const {
      dockerImage
    } = this.props;
    if (!dockerImage || !dockerImage.length) {
      return undefined;
    }
    return this.tools
      .find((aTool) => (aTool.dockerImage || '').toLowerCase() === dockerImage.toLowerCase());
  }

  get filteredTools () {
    const {
      search
    } = this.state;
    const {
      dockerImage
    } = this.props;
    const selected = dockerImage ? dockerImage.toLowerCase() : undefined;
    return this.tools.map(
      (group) => ({
        ...group,
        tools: group.tools
          .filter((tool) => (
            selected &&
            (tool.dockerImage || '').toLowerCase() === selected
          ) || (
            search &&
            search.length >= 3 &&
            tool.dockerImage.toLowerCase().includes(search.toLowerCase()))
          )
      }))
      .filter((group) => group.tools.length > 0);
  }

  onSearch = (search) => this.setState({search});

  onFocus = () => this.setState({search: undefined});

  onChange = (image) => {
    const {
      dockerImage
    } = this.props;
    const docker = dockerImage ? dockerImage.toLowerCase() : undefined;
    const {
      onChange
    } = this.props;
    if (docker !== (image || '').toLowerCase() && typeof onChange === 'function') {
      onChange(image);
    }
  };

  renderSelectGroup = (group) => (
    group.tools.map(tool => {
      const [r, g, iv] = tool.dockerImage.split('/');
      const registry = (tool.registry && tool.registry.description) || r;
      const title = `${registry} > ${g} > ${iv}`;
      return (
        <Select.Option
          key={tool.id}
          value={(tool.dockerImage || '').toLowerCase()}
          title={title}
        >
          <DockerImageDetails docker={tool.dockerImage} />
        </Select.Option>
      );
    })
  );

  render () {
    const {
      className,
      style,
      dockerRegistries,
      disabled,
      dockerImage
    } = this.props;
    const {
      search
    } = this.state;
    const pending = dockerRegistries.pending;
    if (dockerRegistries.error) {
      return (
        <Alert type="error" message={dockerRegistries.error} />
      );
    }
    const notFoundContent = !search
      ? 'Start typing to filter docker images...'
      : (search.length < 3
        ? 'Start typing to filter docker images...' : 'Not found');
    const docker = dockerImage ? dockerImage.toLowerCase() : undefined;
    return (
      <Select
        className={className}
        style={style}
        showSearch
        disabled={pending || disabled}
        value={docker}
        onChange={this.onChange}
        onSearch={this.onSearch}
        onFocus={this.onFocus}
        placeholder="Docker image"
        filterOption={false}
        getPopupContainer={node => node.parentNode}
        notFoundContent={notFoundContent}
      >
        {
          this.filteredTools.map(group => (
            <Select.OptGroup
              key={group.key}
              label={group.label}
            >
              {this.renderSelectGroup(group)}
            </Select.OptGroup>
          ))
        }
      </Select>
    );
  }
}

DockerImageSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  dockerImage: PropTypes.string,
  onChange: PropTypes.func
};

export default DockerImageSelector;
