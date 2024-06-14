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
import classNames from 'classnames';
import {computed} from 'mobx';
import {
  Alert,
  Button, Icon,
  Input,
  Modal,
  Select
} from 'antd';
import roleModel from '../../../../../utils/roleModel';
import highlightText from '../../../../special/highlightText';
import ToolImage from '../../../../../models/tools/ToolImage';
import LoadToolTags from '../../../../../models/tools/LoadToolTags';
import styles from './launch-vs-form.css';

const findGroupByNameSelector = (name) => (group) => {
  return group.name.toLowerCase() === name.toLowerCase();
};
const findGroupByName = (groups, name) => {
  return groups.find(findGroupByNameSelector(name));
};
const processGroupName = (groupName) => {
  if (groupName && groupName.toLowerCase().indexOf('role_') === 0) {
    return groupName.substring('role_'.length);
  }
  return groupName;
};

function getDefaultGroup (groups = [], adGroups = [], userRoles = []) {
  const personal = groups.find(group => group.privateGroup);
  if (personal) {
    return personal;
  }
  const rolesGroups = userRoles.map(r => processGroupName(r.name));
  const candidates = [...adGroups, ...rolesGroups];
  for (let i = 0; i < candidates.length; i++) {
    const group = findGroupByName(groups, candidates[i]);
    if (group) {
      return group;
    }
  }
  return findGroupByName(groups, 'library') ||
    findGroupByName(groups, 'default') ||
    groups[0];
}

@inject('dockerRegistries')
@roleModel.authenticationInfo
@observer
class LaunchVSForm extends React.Component {
  state = {
    tool: undefined,
    filter: undefined,
    tagsError: undefined,
    tagsPending: false,
    tags: [],
    tag: undefined
  };

  @computed
  get groups () {
    const {dockerRegistries} = this.props;
    if (dockerRegistries.loaded) {
      const result = [];
      const {registries = []} = dockerRegistries.value;
      for (let registry of registries) {
        const {groups = []} = registry;
        result.push(...groups.map(group => ({...group, registry})));
      }
      return result;
    }
    return [];
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

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.clearState();
    }
  }

  clearState = () => {
    this.setState({
      tool: undefined,
      filter: undefined,
      tagsError: undefined,
      tagsPending: false,
      tags: [],
      tag: undefined
    });
  };

  onFilterChange = (event) => {
    this.setState({
      filter: event.target.value
    });
  };

  handleLaunch = () => {
    const {onLaunch} = this.props;
    const {tool, tag} = this.state;
    onLaunch && onLaunch(tool, tag);
  };

  handleCustomLaunch = () => {
    const {onLaunchCustom} = this.props;
    const {tool, tag} = this.state;
    onLaunchCustom && onLaunchCustom(tool, tag);
  };

  renderTool = (tool) => {
    const {
      filter: search,
      tool: selectedTool,
      tags,
      tagsError,
      tagsPending,
      tag
    } = this.state;
    const isSelected = selectedTool && selectedTool.id === tool.id;
    const fetchTags = () => {
      this.setState({
        tags: [],
        tagsError: undefined,
        tagsPending: true,
        tag: undefined
      }, () => {
        const request = new LoadToolTags(tool.id);
        request
          .fetch()
          .then(() => {
            if (request.error) {
              throw new Error(request.message);
            } else if (!request.loaded) {
              throw new Error('Error fetching tool versions');
            } else if (!request.value.length) {
              throw new Error('No versions found');
            } else {
              const defaultTag = request.value.find(t => t === 'latest');
              this.setState({
                tags: request.value,
                tagsError: undefined,
                tagsPending: false,
                tag: defaultTag || request.value[0]
              });
            }
          })
          .catch(e => {
            this.setState({
              tags: [],
              tagsError: e.message,
              tagsPending: false,
              tag: undefined
            });
          });
      });
    };
    const toggleSelected = () => {
      if (!isSelected) {
        this.setState({tool}, fetchTags);
      }
    };
    const onChangeTag = (toolTag) => {
      this.setState({
        tag: toolTag
      });
    };
    const renderMainInfo = () => {
      let name = (tool.image || '');
      let group;
      const imageParts = (tool.image || '').split('/');
      if (imageParts.length === 2) {
        [group, name] = imageParts;
      }
      return [
        <div key="name">
          <span style={{fontSize: 'larger', fontWeight: 'bold'}}>
            {highlightText(name, search)}
          </span>
        </div>,
        <div key="description">
          <span style={{fontSize: 'smaller'}}>
            {tool.shortDescription}
          </span>
        </div>,
        <div key="group">
          <span style={{fontSize: 'smaller'}}>
            <span>
              {
                tool.group && tool.group.registry
                  ? (tool.group.registry.description || tool.group.registry.path)
                  : tool.registry
              }
            </span>
            <Icon type="caret-right" style={{fontSize: 'smaller', margin: '0 2px'}} />
            <span>{highlightText(group, search)}</span>
          </span>
        </div>
      ];
    };
    return (
      <div
        key={`${tool.id}`}
        className={
          classNames(
            styles.tool,
            'cp-launch-vs-tool',
            'cp-panel-card',
            {
              [styles.selected]: isSelected,
              'cp-table-element-selected': isSelected,
              'cp-table-element-selected-no-bold': isSelected
            }
          )
        }
        onClick={toggleSelected}
      >
        {
          tool.iconId && (
            <div style={{marginRight: 10, overflow: 'hidden', width: 44, height: 44}}>
              <img
                alt={tool.image}
                src={ToolImage.url(tool.id, tool.iconId)} style={{width: '100%'}}
              />
            </div>
          )
        }
        <div
          className={styles.info}
        >
          {renderMainInfo()}
        </div>
        {
          isSelected && (
            <div
              className={styles.versions}
              onClick={e => e.stopPropagation()}
            >
              {
                tagsPending && (
                  <Icon
                    type="loading"
                  />
                )
              }
              {
                tagsError && (
                  <Alert
                    type="error"
                    message={tagsError}
                  />
                )
              }
              {
                !tagsError && !tagsPending && tags.length > 0 && (
                  <Select
                    value={tag}
                    onChange={onChangeTag}
                    className={styles.select}
                  >
                    {
                      tags.map(tag => (
                        <Select.Option
                          key={tag}
                          value={tag}
                        >
                          {tag}
                        </Select.Option>
                      ))
                    }
                  </Select>
                )
              }
            </div>
          )
        }
        <div
          className={styles.checkbox}
        >
          <Icon type="check-circle" />
        </div>
      </div>
    );
  };

  renderTools = () => {
    const {
      authenticatedUserInfo,
      dockerRegistries
    } = this.props;
    if (dockerRegistries.error || !dockerRegistries.loaded) {
      return null;
    }
    if (this.tools.length === 0) {
      return (
        <Alert
          type="warning"
          message="Tools not found"
        />
      );
    }
    let personalTools = [];
    let personalGroupId;
    if (authenticatedUserInfo.loaded) {
      const {
        groups: adGroups = [],
        roles: userRoles = []
      } = authenticatedUserInfo.value;
      const personalGroup = getDefaultGroup(this.groups, adGroups.slice(), userRoles.slice());
      if (personalGroup) {
        personalGroupId = personalGroup.id;
        personalTools = (personalGroup.tools || []).map(tool => ({
          ...tool,
          group: personalGroup
        }));
      }
    }
    const {
      filter,
      tool: selectedTool
    } = this.state;
    const filterRegEx = filter && filter.length ? (new RegExp(filter, 'i')) : /./;
    const filterTool = (tool) =>
      filterRegEx.test(tool.image) ||
      filterRegEx.test(tool.group.name) ||
      (
        tool.group && tool.group.registry &&
        filterRegEx.test(tool.group.registry.description)
      );
    const filteredPersonalTools = personalTools.filter(filterTool);
    const filteredTools = this.tools
      .filter(filterTool)
      .filter(tool => !personalTools.find(pt => pt.id === tool.id));
    const showAllToolsSection = (filter && filter.length) || personalTools.length === 0;
    return (
      <div
        className={classNames(styles.tools, 'cp-panel', 'cp-panel-transparent')}
      >
        {filteredPersonalTools.map(this.renderTool)}
        {
          (
            showAllToolsSection || (selectedTool && selectedTool.group.id !== personalGroupId)
          ) && (
            <div
              className={styles.divider}
            >
              <div
                className={classNames('cp-divider', 'horizontal')}
              >
                {'\u00A0'}
              </div>
              <div
                className={styles.title}
              >
                Global search
              </div>
              <div
                className={classNames('cp-divider', 'horizontal')}
              >
                {'\u00A0'}
              </div>
            </div>
          )
        }
        {
          !showAllToolsSection &&
          selectedTool &&
          selectedTool.group.id !== personalGroupId &&
          this.renderTool(selectedTool)
        }
        {
          showAllToolsSection && (
            filteredTools.map(this.renderTool)
          )
        }
      </div>
    );
  };

  render () {
    const {
      onClose,
      visible,
      dockerRegistries
    } = this.props;
    const {
      tool,
      filter
    } = this.state;
    const pending = dockerRegistries.pending && !dockerRegistries.loaded;
    const error = dockerRegistries.error ? dockerRegistries.message : undefined;
    return (
      <Modal
        title="Select a tool to launch Versioned Storage"
        visible={visible}
        width="75%"
        onCancel={onClose}
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              disabled={error || pending}
              onClick={this.handleCustomLaunch}
              style={{
                marginRight: 5
              }}
              id="launch-vs-modal-run-custom-btn"
            >
              RUN CUSTOM
            </Button>
            <div>
              <Button
                onClick={onClose}
                id="launch-vs-modal-cancel-btn"
              >
                CANCEL
              </Button>
              <Button
                type="primary"
                disabled={!tool || error || pending}
                onClick={this.handleLaunch}
                id="launch-vs-modal-launch-btn"
              >
                LAUNCH
              </Button>
            </div>
          </div>
        )}
      >
        <div
          className={styles.search}
        >
          <Input
            className={styles.input}
            value={filter}
            onChange={this.onFilterChange}
            placeholder="Filter tools"
          />
        </div>
        {
          error && (
            <Alert
              type="error"
              message={error}
            />
          )
        }
        {this.renderTools()}
      </Modal>
    );
  }
}

LaunchVSForm.propTypes = {
  onClose: PropTypes.func,
  onLaunch: PropTypes.func,
  onLaunchCustom: PropTypes.func,
  visible: PropTypes.bool
};

export default LaunchVSForm;
