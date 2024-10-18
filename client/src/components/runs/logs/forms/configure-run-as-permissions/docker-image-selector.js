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
import {
  Button,
  Icon,
  Select
} from 'antd';
import classNames from 'classnames';
import DockerImageDetails from '../../../../cluster/hot-node-pool/docker-image-details';
import styles from './configure-run-as-permissions.css';

@inject('dockerRegistries')
@observer
class DockerImageSelector extends React.Component {
  state = {
    search: undefined
  }

  @computed
  get tools () {
    const {dockerRegistries, imagesToExclude} = this.props;
    const isDisabled = (tool) => {
      return imagesToExclude &&
        imagesToExclude.length &&
        imagesToExclude.includes(tool.dockerImage);
    };
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
              const dockerImage = `${registry.path}/${tool.image}`;
              if (!isDisabled(dockerImage)) {
                tools.tools.push({
                  ...tool,
                  dockerImage,
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

  get filteredTools () {
    const {
      search
    } = this.state;
    const {toolId} = this.props;
    return this.tools.map(
      (group) => ({
        ...group,
        tools: group.tools
          .filter((tool) => tool.id === toolId ||
            (
              search &&
              search.length >= 3 &&
              tool.dockerImage.toLowerCase().includes(search.toLowerCase())
            )
          )
      }))
      .filter((group) => group.tools.length > 0);
  }

  onChangeDockerImage = (toolId) => {
    const {toolId: currentToolId, onChange} = this.props;
    const id = Number.isNaN(Number(toolId)) ? undefined : Number(toolId);
    if (currentToolId !== id && onChange) {
      onChange(id);
    }
  };

  renderSelectGroup = (group) => {
    return (
      group.tools.map(tool => {
        const [r, g, iv] = tool.dockerImage.split('/');
        const registry = (tool.registry && tool.registry.description) || r;
        const title = `${registry} > ${g} > ${iv}`;
        return (
          <Select.Option
            key={tool.id}
            value={`${tool.id}`}
            title={title}
          >
            <DockerImageDetails docker={tool.dockerImage} />
          </Select.Option>
        );
      })
    );
  };

  render () {
    const {
      disabled,
      duplicate,
      className,
      dockerRegistries,
      style,
      containerStyle,
      onRemove,
      toolId
    } = this.props;
    const {search} = this.state;
    if (dockerRegistries.error) {
      return null;
    }
    const notFoundContent = !search
      ? 'Start typing to filter docker images...'
      : (search.length < 3
        ? 'Start typing to filter docker images...' : 'Not found');
    return (
      <div
        className={className}
        style={style}
      >
        <div
          className={classNames(styles.container)}
          style={containerStyle}
        >
          <Select
            className={classNames({'cp-error': duplicate})}
            showSearch
            disabled={disabled}
            value={toolId === undefined || null ? undefined : `${toolId}`}
            onChange={this.onChangeDockerImage}
            onSearch={(e) => this.setState({search: e})}
            onFocus={() => this.setState({search: undefined})}
            placeholder="Docker image"
            style={{flex: 1}}
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
          <Button
            disabled={disabled}
            size="small"
            type="danger"
            onClick={onRemove}
            className={styles.action}
          >
            <Icon type="delete" />
          </Button>
        </div>
      </div>
    );
  }
}

DockerImageSelector.propTypes = {
  disabled: PropTypes.bool,
  duplicate: PropTypes.bool,
  className: PropTypes.string,
  toolId: PropTypes.number,
  style: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  containerStyle: PropTypes.object,
  imagesToExclude: PropTypes.arrayOf(PropTypes.string)
};

export default DockerImageSelector;
