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
import {Button, Dropdown, Icon, Input, Row} from 'antd';
import registryName from './registryName';
import DockerRegistriesGroupsDropdownContent from './DockerRegistriesGroupsDropdownContent';
import styles from './Tools.css';

@observer
export default class DockerRegistriesNavigation extends React.Component {

  state = {
    groupSearch: null,
    groupsDropDownVisible: false
  };

  renderRegistrySelector = () => {
    if (this.props.currentRegistry) {
      if (this.props.registries.filter(r => r.id !== this.props.currentRegistry.id).length === 0) {
        return (
          <Button
            id="current-registry-button"
            className="single-registry"
            size="small"
            style={{border: 'none', fontWeight: 'bold'}}>
            {registryName(this.props.currentRegistry)}
          </Button>
        );
      }
      const registries = this.props.registries.filter(r => r.id !== this.props.currentRegistry.id)
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
          overlayClassName="registry-dropdown-container"
          overlay={
            <div id="registries-dropdown" className={styles.navigationDropdownContainer} style={{overflowY: 'auto'}}>
              {
                registries.map(registry => {
                  return (
                    <Row key={registry.id} type="flex">
                      <Button
                        id={`registry-${registry.id}-button`}
                        style={{textAlign: 'left', width: '100%', border: 'none'}}
                        onClick={() => this.props.onNavigate && this.props.onNavigate(registry.id)}>
                        {registryName(registry)}
                      </Button>
                    </Row>
                  );
                })
              }
            </div>
          }>
          <Button
            id="current-registry-button"
            size="small"
            style={{border: 'none', fontWeight: 'bold'}}>
            {registryName(this.props.currentRegistry)}
          </Button>
        </Dropdown>
      );
    }
    return null;
  };

  renderGroupSelector = () => {
    if (this.props.currentGroup) {
      const getGroupName = (group) => {
        if (group.privateGroup) {
          return 'personal';
        }
        return group.name;
      };
      const groups = this.props.groups.filter(
        g => this.props.currentGroup.privateGroup
          ? !g.privateGroup
          : g.id !== this.props.currentGroup.id
      ).sort((groupA, groupB) => {
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
      if (groups.length === 0) {
        return (
          <Button
            id="current-group-button"
            className="single-group"
            size="small"
            style={{border: 'none', fontWeight: 'bold'}}>
            {getGroupName(this.props.currentGroup)}
          </Button>
        );
      }
      const onDropDownVisibleChanged = (visible) => {
        this.setState({
          groupsDropDownVisible: visible,
          groupSearch: null
        });
      };
      return (
        <Dropdown
          trigger={['click']}
          visible={this.state.groupsDropDownVisible}
          onVisibleChange={onDropDownVisibleChanged}
          overlay={
            <DockerRegistriesGroupsDropdownContent
              groups={groups}
              isVisible={this.state.groupsDropDownVisible}
              currentGroup={this.props.currentGroup}
              onNavigate={(id) => {
                this.setState({
                  groupsDropDownVisible: false
                }, () => {
                  this.props.onNavigate &&
                  this.props.onNavigate(this.props.currentRegistry.id, id);
                });
              }}
              onCancel={() => {
                this.setState({
                  groupsDropDownVisible: false
                });
              }}
            />
          }>
          <Button
            id="current-group-button"
            size="small"
            style={{border: 'none', fontWeight: 'bold'}}>
            {getGroupName(this.props.currentGroup)}
          </Button>
        </Dropdown>
      );
    }
    return null;
  };

  renderToolSelector = () => {
    return (
      <Input.Search
        id="search-tools-input"
        size="small"
        style={{flex: 1, marginLeft: 10}}
        value={this.props.searchString}
        onChange={e => this.props.onSearch && this.props.onSearch(e.target.value)} />
    );
  };

  render () {
    const registrySelector = this.renderRegistrySelector();
    if (registrySelector) {
      const groupSelector = this.renderGroupSelector();
      const renderToolSelector = this.renderToolSelector();
      return (
        <Row type="flex" align="middle" style={{flex: 1}}>
          {registrySelector}
          {groupSelector && <Icon type="caret-right" />}
          {groupSelector}
          {groupSelector && <Icon type="caret-right" />}
          {groupSelector && renderToolSelector}
        </Row>
      );
    } else {
      return (
        <Row type="flex" />
      );
    }
  }

}

DockerRegistriesNavigation.propTypes = {
  searchString: PropTypes.string,
  registries: PropTypes.array,
  currentRegistry: PropTypes.object,
  groups: PropTypes.array,
  currentGroup: PropTypes.object,
  onNavigate: PropTypes.func,
  onSearch: PropTypes.func
};
