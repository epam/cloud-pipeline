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
import {observer} from 'mobx-react';
import {observable, computed} from 'mobx';
import PropTypes from 'prop-types';
import {Modal, Row, Button, Dropdown, Icon, Table, Input, Select} from 'antd';
import LoadToolTags from '../../../../models/tools/LoadToolTags';
import styles from './Browser.css';
import registryName from '../../../tools/registryName';

@observer
export default class DockerImageBrowser extends React.Component {
  static propTypes = {
    dockerImage: PropTypes.string,
    visible: PropTypes.bool,
    onCancel: PropTypes.func,
    onChange: PropTypes.func,
    registries: PropTypes.array
  };

  state = {
    registry: null,
    group: null,
    tool: null,
    version: null,
    selectedTool: null,
    shorthandNotation: undefined
  };

  @observable _tags = null;

  onSave = () => {
    if (this.props.onChange) {
      this.props.onChange(this.state.selectedTool);
    }
  };

  @computed
  get currentRegistry () {
    const [currentRegistry] = this.state.registry
      ? this.props.registries.filter(g => g.path === this.state.registry)
      : [null];
    return currentRegistry;
  }

  renderRegistrySelector = () => {
    const onSelectRegistry = (registry) => {
      this.setState({
        registry,
        group: null,
        tool: null,
        version: null,
        searchString: null
      }, this.updateGroups);
    };
    if (this.props.registries.filter(r => !this.currentRegistry || r.id !== this.currentRegistry.id).length === 0) {
      return (
        <Button size="small" style={{border: 'none', fontWeight: 'bold'}} onClick={null}>
          {this.currentRegistry ? registryName(this.currentRegistry) : 'Unknown registry'}
        </Button>
      );
    }
    const registries = this.props.registries.filter(r => !this.currentRegistry || r.id !== this.currentRegistry.id)
      .sort((registryA, registryB) => {
        if (registryA.name > registryB.name) {
          return 1;
        } else if (registryA.name < registryB.name) {
          return -1;
        } else {
          return 0;
        }
      });
    return (
      <Dropdown
        trigger={['click']}
        overlay={
          <div className={styles.navigationDropdownContainer}>
            {
              registries.map(registry => {
                return (
                  <Row key={registry.id} type="flex">
                    <Button
                      style={{textAlign: 'left', width: '100%', border: 'none'}}
                      onClick={() => onSelectRegistry(registry.path)}>
                      {registryName(registry)}
                    </Button>
                  </Row>
                );
              })
            }
          </div>
        }>
        <Button size="small" style={{border: 'none', fontWeight: 'bold'}}>
          {this.currentRegistry ? registryName(this.currentRegistry) : 'Unknown registry'}
        </Button>
      </Dropdown>
    );
  };

  @computed
  get groups () {
    if (!this.currentRegistry) {
      return [];
    }
    return (this.currentRegistry.groups || []).map(g => g);
  }

  @computed
  get currentGroup () {
    const [currentGroup] = this.state.group ? this.groups.filter(g => g.name === this.state.group) : [null];
    return currentGroup;
  }

  renderGroupSelector = () => {
    const onSelectGroup = (group) => {
      this.setState({
        group,
        tool: null,
        version: null,
        groupsDropDownVisible: false,
        groupSearch: null,
        searchString: null
      }, this.updateTools);
    };
    const renderGroupName = (group) => {
      if (group.privateGroup) {
        return 'personal';
      }
      return group.name;
    };
    if (!this.currentGroup) {
      return null;
    }
    if (this.groups.filter(r => r.id !== this.currentGroup.id).length === 0) {
      return [
        <Icon type="caret-right" key="group-arrow" />,
        <Button key="group" size="small" style={{border: 'none', fontWeight: 'bold'}} onClick={null}>
          {this.currentGroup ? renderGroupName(this.currentGroup) : 'Unknown group'}
        </Button>
      ];
    }
    let groups = this.groups.filter(r => !r.privateGroup && (!this.currentGroup || r.id !== this.currentGroup.id));
    if (!this.currentGroup.privateGroup) {
      const [privateGroup] = this.groups.filter(r => r.privateGroup);
      if (privateGroup) {
        groups = [privateGroup, ...groups];
      }
    }
    groups = groups.sort((groupA, groupB) => {
      if (groupA.privateGroup && !groupB.privateGroup) {
        return -1;
      } else if (groupB.privateGroup && !groupA.privateGroup) {
        return 1;
      } else if (groupA.privateGroup && groupB.privateGroup) {
        return 0;
      } else if (groupA.name > groupB.name) {
        return 1;
      } else if (groupA.name < groupB.name) {
        return -1;
      } else {
        return 0;
      }
    });
    const onDropDownVisibleChanged = (visible) => {
      this.setState({
        groupsDropDownVisible: visible,
        groupSearch: null
      });
    };
    const onGroupSearch = (e) => {
      this.setState({
        groupSearch: e.target.value
      });
    };
    return [
      <Icon type="caret-right" key="group-arrow" />,
      <Dropdown
        visible={this.state.groupsDropDownVisible}
        onVisibleChange={onDropDownVisibleChanged}
        key="group"
        trigger={['click']}
        overlay={
          <Row className={styles.navigationDropdownContainer}>
            <Row type="flex">
              <Input.Search
                value={this.state.groupSearch}
                onChange={onGroupSearch}
                style={{width: '100%', margin: '13px 13px 4px 13px'}}
                size="small"
                onKeyDown={(e) => {
                  if (e.key && e.key.toLowerCase() === 'escape') {
                    onDropDownVisibleChanged(false);
                  }
                }}
              />
            </Row>
            {
              groups.filter(g => !this.state.groupSearch || !this.state.groupSearch.length ||
              g.name.toLowerCase().indexOf(this.state.groupSearch.toLowerCase()) >= 0).map(group => {
                return (
                  <Row key={group.id} type="flex">
                    <Button
                      style={{
                        textAlign: 'left',
                        width: '100%',
                        border: 'none',
                        fontWeight: group.privateGroup ? 'bold' : 'normal',
                        fontStyle: group.privateGroup ? 'italic' : 'normal'
                      }}
                      onClick={() => onSelectGroup(group.name)}>
                      {renderGroupName(group)}
                    </Button>
                  </Row>
                );
              })
            }
          </Row>
        }>
        <Button size="small" style={{border: 'none', fontWeight: 'bold'}}>
          {renderGroupName(this.currentGroup)}
        </Button>
      </Dropdown>
    ];
  };

  renderToolSelector = () => {
    const onSearch = (event) => {
      const text = event.target.value;
      this.setState({searchString: text});
    };
    if (this.currentGroup) {
      return [
        <Icon key="tool-icon" type="caret-right" />,
        <Input.Search
          key="tool-search"
          style={{flex: 1, marginLeft: 10}}
          value={this.state.searchString}
          placeholder="Tool"
          onChange={onSearch}
          size="small" />
      ];
    }
    return null;
  };

  @computed
  get tools () {
    if (!this.currentGroup) {
      return [];
    }
    const toolMatches = (tool) => {
      if (!this.state.searchString || !this.state.searchString.length) {
        return true;
      }
      return tool.image.toLowerCase().indexOf(this.state.searchString.toLowerCase()) >= 0;
    };
    return (this.currentGroup.tools || []).map(t => t).filter(toolMatches).sort((toolA, toolB) => {
      if (toolA.image > toolB.image) {
        return 1;
      } else if (toolA.image < toolB.image) {
        return -1;
      } else {
        return 0;
      }
    });
  }

  renderToolsTable = () => {
    const parseToolName = (tool) => {
      const toolNameParts = tool.image.split('/');
      let toolName;
      if (toolNameParts.length >= 2) {
        toolName = toolNameParts[1];
      } else {
        toolName = toolNameParts[0];
      }
      return toolName;
    };
    const toolIsSelected = (tool) => {
      return parseToolName(tool) === this.state.tool;
    };
    const onSelect = async (tool) => {
      this._tags = new LoadToolTags(tool.id);
      await this._tags.fetch();
      const options = this._tags.loaded ? (this._tags.value || []).map(t => t) : [];
      let selectedTool = this.state.registry && this.state.shorthandNotation !== this.state.registry
        ? `${this.state.registry}/${tool.image}`
        : tool.image;
      let version;
      if (options.length > 0) {
        const [tag] = options.filter(tag => tag === 'latest');
        if (tag) {
          version = tag;
          selectedTool = `${selectedTool}:${tag}`;
        } else {
          version = options[0];
          selectedTool = `${selectedTool}:${options[0]}`;
        }
      }
      this.setState({
        selectedTool,
        tool: parseToolName(tool),
        version
      });
    };
    const onSelectTag = (tool) => (tag) => {
      this.setState({
        version: tag,
        selectedTool: this.state.registry && this.state.shorthandNotation !== this.state.registry
          ? `${this.state.registry}/${tool.image}:${tag}`
          : `${tool.image}:${tag}`,
        tool: parseToolName(tool)
      });
    };
    const columns = [
      {
        dataIndex: 'image',
        key: 'image',
        onCellClick: (tool) => onSelect(tool),
        render: (image, tool) => {
          if (toolIsSelected(tool)) {
            return (
              <span>
                <Icon type="check-circle" style={{width: 20}} />
                {image}
              </span>
            );
          } else {
            return <span style={{marginLeft: 20}}>{image}</span>;
          }
        }
      },
      {
        key: 'version',
        className: styles.toolColumnTags,
        onCellClick: (tool) => {
          if (!toolIsSelected(tool) || !this._tags) {
            return onSelect(tool);
          }
        },
        render: (tool) => {
          if (toolIsSelected(tool) && this._tags) {
            if (this._tags.pending) {
              return <Icon type="loading" />;
            }
            if (this._tags.error) {
              return <i>Error loading tags</i>;
            }
            const options = this._tags.loaded ? (this._tags.value || []).map(t => t) : [];
            return (
              <Select
                onSelect={onSelectTag(tool)}
                value={this.state.version}
                style={{width: '100%'}}
                size="small">
                {
                  options.map(tag => {
                    return (
                      <Select.Option key={tag} data={tag}>{tag}</Select.Option>
                    );
                  })
                }
              </Select>
            );
          }
          return null;
        }
      }
    ];
    return (
      <Row type="flex" style={{height: '40vh', overflowY: 'auto'}}>
        <Table
          className={styles.table}
          dataSource={this.tools}
          columns={columns}
          loading={this._tags ? this._tags.pending : false}
          showHeader={false}
          rowKey="id"
          rowClassName={() => styles.toolRow}
          pagination={false}
          style={{width: '100%'}}
          locale={{emptyText: 'No tools'}}
          size="small" />
      </Row>
    );
  };

  render () {
    return (
      <Modal
        width="50%"
        title="Select docker image"
        visible={this.props.visible}
        onCancel={this.props.onCancel}
        onOk={this.onSave}>
        <Row type="flex" align="middle" style={{marginBottom: 10}}>
          {this.renderRegistrySelector()}
          {this.renderGroupSelector()}
          {this.renderToolSelector()}
        </Row>
        {this.renderToolsTable()}
      </Modal>
    );
  }

  parseDockerImage = (image) => {
    if (this.state.selectedTool !== image) {
      if (!image || image.trim().length === 0) {
        this.setState({
          registry: null,
          group: null,
          tool: null,
          version: null,
          selectedTool: image,
          searchString: null,
          shorthandNotation: undefined,
        });
      } else {
        const parts = image.split('/');
        let version;
        let tool = parts.pop();
        const group = parts.pop() || '';
        let registry = parts.pop();
        let shorthandNotation;
        if (tool) {
          const toolParts = tool.split(':');
          if (toolParts.length === 2) {
            tool = toolParts[0];
            version = toolParts[1];
          }
        }
        if (!registry && this.props.registries.length > 0) {
          let registryCandidate;
          for (let i = 0; i < this.props.registries.length; i++) {
            const r = this.props.registries[i];
            for (let j = 0; j < r.groups.length; j++) {
              if (r.groups[j].name.toLowerCase() === group.toLowerCase()) {
                registryCandidate = r.path;
                for (let t = 0; t < r.groups[j].tools.length; t++) {
                  if (r.groups[j].tools[t].image.toLowerCase() === tool.toLowerCase()) {
                    break;
                  }
                }
              }
            }
          }
          registry = registryCandidate;
          shorthandNotation = registryCandidate;
        }
        this.setState({
          registry,
          group,
          tool,
          version,
          selectedTool: image,
          searchString: null,
          shorthandNotation
        }, this.updateTools);
      }
    }
  };

  componentWillReceiveProps (nextProps) {
    if (nextProps.dockerImage !== this.props.dockerImage || nextProps.visible) {
      this.parseDockerImage(nextProps.dockerImage);
    }
    if (nextProps.visible) {
      this.loadToolTags();
    }
  }

  updateGroups = async () => {
    if (this.currentRegistry) {
      if (!this.state.group) {
        if (this.groups.length === 1) {
          this.setState({
            group: this.groups[0].name
          }, this.updateTools);
        } else if (this.groups.length > 1) {
          const [privateGroup] = this.groups.filter(g => g.privateGroup);
          if (privateGroup) {
            this.setState({
              group: privateGroup.name
            }, this.updateTools);
          } else {
            this.setState({
              group: this.groups[0].name
            }, this.updateTools);
          }
        }
      }
    }
  };

  loadToolTags = async () => {
    if (!this.currentGroup) {
      return;
    }
    const [tool] = this.tools.filter(t => t.image === `${this.currentGroup.name}/${this.state.tool}`);
    if (tool) {
      this._tags = new LoadToolTags(tool.id);
      await this._tags.fetch();
      const tags = this._tags.loaded ? (this._tags.value || []).map(t => t) : [];
      const [tag] = tags.filter(tag => tag === (this.state.version || 'latest'));
      if (tag) {
        this.setState({
          version: tag
        });
      } else if (tags.length > 0) {
        this.setState({
          version: tags[0]
        });
      }
    } else {
      this._tags = null;
    }
  };

  updateTools = async () => {
    if (this.currentGroup) {
      await this.loadToolTags();
    }
  };

  componentDidUpdate (nextProps, nextState) {
    if (nextState.registry !== this.state.registry || nextProps.registries !== this.props.registries) {
      this.updateGroups();
    } else if (!this.state.registry && this.props.registries && this.props.registries.length > 0) {
      this.setState({
        registry: this.props.registries[0].path
      }, this.updateGroups);
    }
    if (!this.state.group && this.state.registry) {
      if (this.groups.length === 1) {
        this.setState({
          group: this.groups[0].name
        }, this.updateTools);
      } else if (this.groups.length > 1) {
        const [privateGroup] = this.groups.filter(g => g.privateGroup);
        if (privateGroup) {
          this.setState({
            group: privateGroup.name
          }, this.updateTools);
        } else {
          this.setState({
            group: this.groups[0].name
          }, this.updateTools);
        }
      }
    }
    if (this.state.group && nextState.group !== this.state.group) {
      this.updateTools();
    }
  }
}
