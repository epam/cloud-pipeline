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
  Alert,
  Button,
  Icon,
  Select
} from 'antd';
import classNames from 'classnames';
import DockerImageDetails from './docker-image-details';
import MultiSelect from '../../special/multiSelect';
import LoadToolTags from '../../../models/tools/LoadToolAttributes';
import styles from './add-docker-registry-control.css';

@inject('dockerRegistries')
@observer
class AddDockerRegistryControl extends React.Component {
  state = {
    docker: undefined,
    version: undefined,
    versionsSelected: [],
    versionsPending: false,
    pending: false,
    versions: [],
    versionsWithIdentifiers: [],
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
    const {docker, multipleMode, versionsSelected} = this.props;
    if (multipleMode && docker) {
      this.setState({
        docker: docker,
        versionsSelected: versionsSelected || []
      }, this.fetchVersions);
    } else if (!multipleMode && docker) {
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
        versionsWithIdentifiers: [],
        versionsHash: undefined,
        versionsSelected: []
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

  getToolId = (docker) => {
    const [r, g] = docker.split('/');
    const currentTool = this.tools.find(tool => tool.key === `${r}/${g}`);
    if (currentTool) {
      const currentImage = (currentTool.tools || [])
        .find(image => image.dockerImage === docker);
      return (currentImage || {}).id;
    }
    return undefined;
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
          versions: [],
          versionsWithIdentifiers: [],
          versionsPending: true
        }, () => {
          fn()
            .then((versions) => {
              const versionsArray = (versions || [])
                .slice()
                .map(v => v.version);
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
                versions: versionsArray,
                versionsWithIdentifiers: versions.slice(),
                version: v,
                versionsPending: false
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
                    const {versions = []} = versionsRequest.value || {};
                    const nonWindowsVersions = versions
                      .filter(v => !v.attributes || !/^windows$/i.test(v.attributes.platform))
                      .map(v => ({id: v.attributes.id, version: v.version}));
                    resolve(nonWindowsVersions);
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
        version: 'latest',
        versionsSelected: []
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

  onChangeMultipleVersions = (versions) => {
    const {onChange} = this.props;
    const {docker, versionsWithIdentifiers = []} = this.state;
    const versionsSelected = (versions || [])
      .map(name => versionsWithIdentifiers.find(v => v.version === name))
      .filter(Boolean);
    const currentToolId = this.getToolId(docker);
    return this.setState({versionsSelected}, () => {
      onChange && onChange(docker, versionsSelected, currentToolId);
    });
  };

  handleChange = () => {
    const {onChange, multipleMode} = this.props;
    const {
      docker,
      version,
      versionsSelected
    } = this.state;
    if (docker && multipleMode) {
      const currentToolId = this.getToolId(docker);
      return onChange(docker, versionsSelected, currentToolId);
    }
    if (onChange && docker && version) {
      return onChange(`${docker}:${version}`);
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
          getPopupContainer={node => node.parentNode}
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

  renderMultipleVersionsSelector = () => {
    const {
      pending,
      versions,
      versionsSelected,
      versionsPending
    } = this.state;
    if (versions.length > 0) {
      return (
        <div style={{
          marginLeft: 'auto',
          display: 'flex',
          flexWrap: 'nowrap',
          alignItems: 'center'
        }}>
          <span
            key="label"
            style={{
              marginLeft: 5,
              fontWeight: 'bold',
              verticalAlign: 'center'
            }}
          >
            Versions:
          </span>
          <MultiSelect
            onChange={this.onChangeMultipleVersions}
            values={(versionsSelected || []).map(v => v.version)}
            options={versions}
            pending={pending || versionsPending}
          />
        </div>
      );
    }
  };

  renderSelectGroup = (group) => {
    const {imagesToExclude} = this.props;
    const isDisabled = (tool) => {
      return imagesToExclude &&
      imagesToExclude.length &&
      imagesToExclude.includes(tool.dockerImage);
    };
    return (
      group.tools.map(tool => (
        <Select.Option
          key={tool.id}
          value={tool.dockerImage}
          style={{
            background: isDisabled(tool)
              ? '#dfdfdf'
              : 'none'
          }}
          disabled={isDisabled(tool)}
        >
          <DockerImageDetails docker={tool.dockerImage} />
        </Select.Option>
      ))
    );
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
      containerStyle,
      onRemove,
      multipleMode
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
          style={containerStyle}
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
            getPopupContainer={node => node.parentNode}
          >
            {
              this.tools.map(group => (
                <Select.OptGroup key={group.key} label={group.label}>
                  {this.renderSelectGroup(group)}
                </Select.OptGroup>
              ))
            }
          </Select>
          {multipleMode
            ? this.renderMultipleVersionsSelector()
            : this.renderVersionsSelector()
          }
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
  onRemove: PropTypes.func,
  multipleMode: PropTypes.bool,
  versionsSelected: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  containerStyle: PropTypes.object,
  imagesToExclude: PropTypes.arrayOf(PropTypes.string)
};

AddDockerRegistryControl.defaultProps = {
  showDelete: true
};

export default AddDockerRegistryControl;
