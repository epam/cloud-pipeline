/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
  Button,
  Icon,
  Select
} from 'antd';
import classNames from 'classnames';
import DockerImageDetails from './docker-image-details';
import LoadToolTags from '../../../models/tools/LoadToolTags';
import styles from './add-docker-registry-control.css';

@inject('dockerRegistries')
@observer
class AddDockerRegistryControl extends React.Component {
  state = {
    docker: undefined,
    version: undefined,
    pending: false,
    versions: [],
    versionsHash: undefined
  }

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.docker !== this.props.docker) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {docker} = this.props;
    if (docker) {
      const [r, g, iv] = docker.split('/');
      const [i, v] = iv.split(':');
      this.setState({
        docker: [r, g, i].join('/'),
        version: v || 'latest'
      }, this.fetchVersions);
    } else {
      this.setState({
        docker: undefined,
        version: undefined,
        versions: [],
        versionsHash: undefined
      });
    }
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
    return result;
  }

  getTool = (docker) => {
    const {dockerRegistries} = this.props;
    if (!docker) {
      return Promise.resolve(null);
    } else {
      return new Promise((resolve) => {
        dockerRegistries.fetchIfNeededOrWait()
          .then(() => {
            const {registries = []} = dockerRegistries.value;
            for (let r = 0; r < registries.length; r++) {
              const {groups = []} = registries[r];
              for (let g = 0; g < groups.length; g++) {
                const {tools = []} = groups[g];
                for (let t = 0; t < tools.length; t++) {
                  const image = `${registries[r].path}/${tools[t].image}`.toLowerCase();
                  if (docker.toLowerCase() === image) {
                    resolve(tools[t]);
                    return;
                  }
                }
              }
            }
            resolve(null);
          })
          .catch(() => resolve(null));
      });
    }
  };

  fetchVersions = () => {
    const {
      docker,
      version: currentVersion,
      versionsHash
    } = this.state;
    if (docker !== versionsHash) {
      const wrapper = fn => () => {
        this.setState({
          versionsHash: docker,
          versions: []
        }, () => {
          fn()
            .then(versions => {
              const versionsArray = (versions || []).slice();
              let v = currentVersion;
              if (versionsArray.indexOf(v) === -1) {
                if (versionsArray.indexOf('latest') >= 0) {
                  v = 'latest';
                } else {
                  v = versionsArray[0];
                }
              }
              this.setState({
                pending: false,
                versions: (versions || []).slice(),
                version: v
              }, () => {
                this.handleChange();
              });
            });
        });
      };
      wrapper(() => new Promise((resolve) => {
        this.getTool(docker)
          .then(tool => {
            if (!tool) {
              resolve([]);
            } else {
              const {id} = tool;
              const versionsRequest = new LoadToolTags(id);
              versionsRequest
                .fetch()
                .then(() => {
                  if (versionsRequest.loaded) {
                    resolve(versionsRequest.value);
                  } else {
                    resolve([]);
                  }
                })
                .catch(() => {
                  resolve([]);
                });
            }
          });
      }))();
    }
  };

  onChangeDockerImage = (image) => {
    if (this.state.docker !== image) {
      this.setState({
        docker: image,
        version: 'latest'
      }, this.fetchVersions);
    }
  };

  onChangeDockerVersion = (version) => {
    if (version !== this.state.version) {
      this.setState({
        version: version
      }, this.handleChange);
    }
  };

  handleChange = () => {
    const {onChange} = this.props;
    const {docker, version} = this.state;
    if (onChange && docker && version) {
      onChange(`${docker}:${version}`);
    }
  };

  renderVersionsSelector = () => {
    const {disabled} = this.props;
    const {pending, version, versions} = this.state;
    if (versions.length > 0) {
      return [
        <span
          key="label"
          style={{marginLeft: 5, fontWeight: 'bold'}}>
          Version:
        </span>,
        <Select
          className={styles.select}
          key="version-selector"
          disabled={pending || disabled || (versions.length === 1 && !!version)}
          value={version}
          onChange={this.onChangeDockerVersion}
          style={{width: 200, marginLeft: 5}}
        >
          {
            versions.map((v) => (
              <Select.Option key={v} value={v}>
                {v}
              </Select.Option>
            ))
          }
        </Select>
      ];
    }
    return null;
  };

  render () {
    const {
      disabled,
      duplicate,
      className,
      dockerRegistries,
      showError,
      showDelete,
      style,
      onRemove
    } = this.props;
    const {pending, docker} = this.state;
    if (dockerRegistries.error) {
      if (showError) {
        return (
          <Alert type="error" message={dockerRegistries.error} />
        );
      }
      return null;
    }
    return (
      <div
        className={className}
        style={style}
      >
        <div
          className={
            classNames(
              styles.container,
              {
                [styles.duplicate]: duplicate
              }
            )
          }
        >
          <Select
            className={styles.select}
            showSearch
            disabled={pending || disabled}
            value={docker}
            onChange={this.onChangeDockerImage}
            placeholder="Docker image"
            style={{flex: 1}}
            filterOption={(input, option) =>
              option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
          >
            {
              this.tools.map(group => (
                <Select.OptGroup key={group.key} label={group.label}>
                  {
                    group.tools.map(tool => (
                      <Select.Option
                        key={tool.dockerImage}
                        value={tool.dockerImage}
                      >
                        <DockerImageDetails docker={tool.dockerImage} />
                      </Select.Option>
                    ))
                  }
                </Select.OptGroup>
              ))
            }
          </Select>
          {this.renderVersionsSelector()}
          {
            showDelete && (
              <Button
                disabled={disabled}
                size="small"
                type="danger"
                onClick={onRemove}
                className={styles.action}
              >
                <Icon type="delete" />
              </Button>
            )
          }
        </div>
      </div>
    );
  }
}

AddDockerRegistryControl.propTypes = {
  disabled: PropTypes.bool,
  duplicate: PropTypes.bool,
  className: PropTypes.string,
  docker: PropTypes.string,
  showError: PropTypes.bool,
  showDelete: PropTypes.bool,
  style: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func
};

AddDockerRegistryControl.defaultProps = {
  showDelete: true
}

export default AddDockerRegistryControl;
