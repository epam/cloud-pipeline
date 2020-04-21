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

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Input, Row, AutoComplete, Select} from 'antd';
import roleModel from '../../../../utils/roleModel';
import RegistrySelector from '../../../tools/selectors/RegistrySelector';
import ToolGroupSelector from '../../../tools/selectors/ToolGroupSelector';
import LoadToolTags from '../../../../models/tools/LoadToolTags';
import styles from './CommitRunDockerImageInput.css';

@observer
export default class CommitRunDockerImageInput extends React.Component {
  static propTypes = {
    value: PropTypes.string,
    onChange: PropTypes.func,
    disabled: PropTypes.bool,
    registries: PropTypes.array,
    visible: PropTypes.bool,
    onPressEnter: PropTypes.func,
    onValidation: PropTypes.func
  };

  state = {};

  @observable _tags;

  updateGroups = () => {
    if (this.currentRegistry && this.groups.length > 0 && !this.state.group) {
      let groupName;
      const [privateGroup] = this.groups.filter(g => g.privateGroup);
      if (privateGroup) {
        groupName = privateGroup.name;
      } else {
        groupName = this.groups[0].name;
      }
      if (groupName) {
        this.setState({
          group: groupName
        }, this.updateTags);
      }
    } else {
      this.updateTags();
    }
  };

  onSelectRegistry = (path) => {
    if (this.state.registry !== path) {
      this.setState({
        registry: path,
        group: null
      }, async () => {
        this.handleOnChange();
        this.updateGroups();
        this.updateTags();
      });
    }
  };

  onSelectGroup = (name) => {
    if (this.state.group !== name) {
      this.setState({
        group: name
      }, () => {
        this.handleOnChange();
        this.updateTags();
      });
    } else {
      this.handleOnChange();
    }
  };

  onToolChanged = (tool) => {
    this.setState({
      tool: tool
    }, async () => {
      await this.updateTags();
      this.validateTool();
    });
  };

  validateTool = () => {
    if (!this.currentGroup) {
      this.props.onValidation && this.props.onValidation(false);
    } else {
      const allTools = (this.currentGroup.tools || [])
        .map(t => t)
        .filter(t => roleModel.writeAllowed(t))
        .map(t => t.image.split('/')[1]);
      if (!roleModel.writeAllowed(this.currentGroup) && allTools.filter(t => t === this.state.tool).length === 0) {
        this.props.onValidation && this.props.onValidation(false);
      } else {
        this.props.onValidation && this.props.onValidation(true);
      }
    }
  };

  checkTool = () => {
    if (!this.currentGroup) {
      return [];
    }
    const allTools = (this.currentGroup.tools || [])
      .map(t => t)
      .filter(t => roleModel.writeAllowed(t))
      .map(t => t.image.split('/')[1]);
    if (allTools.length > 0 && !roleModel.writeAllowed(this.currentGroup) && allTools.filter(t => t === this.state.tool).length === 0) {
      this.setState({
        tool: allTools[0]
      }, async () => {
        await this.updateTags();
        this.props.onValidation && this.props.onValidation(true);
      });
    }
  };

  updateTags = async () => {
    const [group] = this.groups.filter(g => g.name === this.state.group);
    if (group) {
      const [tool] = (group.tools || []).filter(t => t.image === `${this.state.group}/${this.state.tool}`);
      if (tool) {
        this._tags = new LoadToolTags(tool.id);
        await this._tags.fetch();
        const [defaultTag] = (this._tags.value || []).filter(t => t === this.state.version) || (this._tags.value || []);
        this.setState({
          version: defaultTag
        }, this.handleOnChange);
        return;
      }
    }
    this._tags = null;
    this.setState({
      version: null
    }, this.handleOnChange);
  };

  @computed
  get toolVersions () {
    if (this._tags && this._tags.loaded) {
      return (this._tags.value || []).map(t => t);
    }
    return [];
  }

  @computed
  get tags () {
    const tags = this.toolVersions.map(tag => {
      return {
        name: tag,
        isNew: false
      };
    });
    if (this.state.version && this.state.version.length &&
      this.toolVersions.map(t => t.toLowerCase()).indexOf(this.state.version.toLowerCase()) === -1) {
      return [
        {
          name: this.state.version,
          isNew: true
        },
        ...tags
      ];
    }
    return tags;
  }

  getValue = () => {
    if (this.state.registry && this.state.group && this.state.tool) {
      if (this.state.version) {
        return `${this.state.registry}/${this.state.group}/${this.state.tool}:${this.state.version}`;
      } else {
        return `${this.state.registry}/${this.state.group}/${this.state.tool}`;
      }
    } else {
      return null;
    }
  };

  handleOnChange = () => {
    if (!this.props.onChange || (!this.state.group && this.state.registry)) {
      return;
    }
    this.props.onChange(this.getValue());
    this.validateTool();
  };

  @computed
  get registries () {
    return this.props.registries;
  }

  @computed
  get groups () {
    if (!this.currentRegistry) {
      return [];
    }
    return (this.currentRegistry.groups || [])
      .map(g => g)
      .filter(g => roleModel.writeAllowed(g) ||
        (g.tools || []).filter(t => roleModel.writeAllowed(t)).length > 0
      );
  }

  @computed
  get tools () {
    if (!this.currentGroup) {
      return [];
    }
    const originalTools = (this.currentGroup.tools || []).map(t => t).filter(t => roleModel.writeAllowed(t));
    const tools = originalTools.map(tool => {
      const image = tool.image.split('/')[1];
      return {
        name: image,
        isNew: false,
        tool: tool
      };
    }).filter(t => !this.state.tool || t.name.toLowerCase().indexOf(this.state.tool.toLowerCase()) === 0);
    if (this.state.tool && this.state.tool.length &&
        roleModel.writeAllowed(this.currentGroup) &&
        tools.map(t => t.name.toLowerCase()).indexOf(this.state.tool.toLowerCase()) === -1) {
      return [
        {
          name: this.state.tool,
          isNew: true
        },
        ...tools
      ];
    }
    return tools;
  }

  @computed
  get currentRegistry () {
    const [currentRegistry] = this.state.registry
      ? this.registries.filter(g => g.path === this.state.registry)
      : [null];
    return currentRegistry;
  }

  @computed
  get currentGroup () {
    const [currentGroup] = this.state.group
      ? this.groups.filter(g => g.name === this.state.group)
      : [null];
    return currentGroup;
  }

  onVersionChanged = (value) => {
    this.setState({
      version: value
    }, this.handleOnChange);
  };

  render () {
    if (this.state.value === undefined) {
      return (
        <Input
          disabled={true}
          size="large" />
      );
    }
    return (
      <Row type="flex">
        <div
          style={{
            backgroundColor: '#eee',
            border: '1px solid #ccc',
            borderRadius: '4px 0px 0px 4px',
            height: 32
          }}>
          <RegistrySelector
            disabled={this.props.disabled}
            value={this.state.registry}
            registries={this.registries}
            emptyValueMessage="Select registry"
            onChange={this.onSelectRegistry} />
          {
            this.state.group && <b>/</b>
          }
          <ToolGroupSelector
            disabled={this.props.disabled}
            value={this.state.group}
            groups={this.groups}
            onChange={this.onSelectGroup}
            emptyValueMessage="Select group" />
        </div>
        <Select
          mode="combobox"
          className={styles.toolAutocomplete}
          disabled={this.props.disabled}
          ref={this.initializeNameInput}
          size="large"
          value={this.state.tool}
          filterOption={false}
          onBlur={this.checkTool}
          optionLabelProp="text"
          style={{
            width: 200,
            flex: 1,
            borderRadius: '0px 4px 4px 0px',
            marginLeft: -1
          }}
          onChange={this.onToolChanged}
          placeholder="Tool">
          {
            this.tools.map(tool => {
              return (
                <Select.Option key={tool.name} text={tool.name}>
                  {
                    tool.isNew
                      ? `Add new tool '${tool.name}'`
                      : tool.name
                  }
                </Select.Option>
              );
            })
          }
        </Select>
        <AutoComplete
          disabled={this.props.disabled}
          size="large"
          value={this.state.version}
          optionLabelProp="text"
          style={{width: 200, marginLeft: 10}}
          onChange={this.onVersionChanged}
          placeholder="Version">
          {
            this.tags.map(tag => {
              return (
                <AutoComplete.Option key={tag.name} text={tag.name}>
                  {
                    tag.isNew
                      ? `Add new version '${tag.name}'`
                      : tag.name
                  }
                </AutoComplete.Option>
              );
            })
          }
        </AutoComplete>
      </Row>
    );
  }

  updateState = (props) => {
    props = props || this.props;
    if (props.value && props.value.length && this.state.value !== props.value) {
      const parts = props.value.split('/');
      if (parts.length === 3) {
        const afterUpdate = this.state.registry !== parts[0] ? this.updateGroups : undefined;
        const [toolName, toolVersion] = (parts[2] || '').split(':');
        this.setState({
          value: props.value,
          registry: parts[0],
          group: parts[1],
          tool: toolName,
          version: toolVersion
        }, afterUpdate);
      }
    } else {
      this.setState({
        value: null
      });
    }
  };

  componentWillReceiveProps (nextProps) {
    this.updateState(nextProps);
  }

  componentDidMount () {
    this.updateState();
    this.focusNameInput();
  }

  initializeNameInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.nameInput = input.refs.input;
      this.nameInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
      this.focusNameInput();
    }
  };

  focusNameInput = () => {
    if (this.props.visible && this.nameInput) {
      setTimeout(() => {
        this.nameInput.focus();
      }, 0);
    }
  };

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
    if (!this.state.group && this.currentRegistry) {
      this.updateGroups();
    }
  }
}
