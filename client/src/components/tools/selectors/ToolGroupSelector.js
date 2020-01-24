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
import {Row, Button, Dropdown, Input, Icon} from 'antd';
import styles from './Selectors.css';
import compareArrays from '../../../utils/compareArrays';

@observer
export default class ToolGroupSelector extends React.Component {
  static propTypes = {
    onChange: PropTypes.func,
    value: PropTypes.string,
    emptyValueMessage: PropTypes.string,
    disabled: PropTypes.bool,
    groups: PropTypes.array
  };

  state = {
    value: null
  };

  onSelectGroup = (name) => {
    this.setState({
      value: name,
      groupsDropDownVisible: false,
      groupSearch: null
    });
    if (this.props.onChange) {
      this.props.onChange(name);
    }
  };

  get currentGroup () {
    return this.state.value ? this.props.groups.filter(r => r.name === this.state.value)[0] : null;
  }

  render () {
    if (this.props.groups && this.props.groups.length === 0 && !this.state.value) {
      return <Icon type="loading" />;
    }
    const renderGroupName = (group) => {
      if (group.privateGroup) {
        return 'personal';
      }
      return group.name;
    };
    if (this.props.disabled ||
      this.props.groups.filter(r => !this.currentGroup || r.id !== this.currentGroup.id).length === 0) {
      return (
        <Button
          key="group"
          size="small"
          style={{border: 'none', fontWeight: 'bold', backgroundColor: 'transparent'}}
          onClick={null}>
          {this.currentGroup ? renderGroupName(this.currentGroup) : this.state.value || 'Unknown group'}
        </Button>
      );
    }
    let groups = this.props.groups.filter(r => !r.privateGroup && (!this.currentGroup || r.id !== this.currentGroup.id));
    if (!this.currentGroup || !this.currentGroup.privateGroup) {
      const [privateGroup] = this.props.groups.filter(r => r.privateGroup);
      if (privateGroup) {
        groups = [privateGroup, ...groups];
      }
    }
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
    return (
      <Dropdown
        visible={this.state.groupsDropDownVisible}
        onVisibleChange={onDropDownVisibleChanged}
        key="group"
        trigger={['click']}
        overlay={
          <div className={styles.navigationDropdownContainer}>
            <Row type="flex">
              <Input.Search
                value={this.state.groupSearch}
                onChange={onGroupSearch}
                style={{width: '100%', margin: '13px 13px 4px 13px'}}
                onKeyDown={(e) => {
                  if (e.key && e.key.toLowerCase() === 'escape') {
                    onDropDownVisibleChanged(false);
                  }
                }}
                size="small"
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
                      onClick={() => this.onSelectGroup(group.name)}>
                      {renderGroupName(group)}
                    </Button>
                  </Row>
                );
              })
            }
          </div>
        }>
        <Button size="small" style={{border: 'none', fontWeight: 'bold', backgroundColor: 'transparent'}}>
          {this.currentGroup ? renderGroupName(this.currentGroup) : this.state.value || 'Unknown group'}
        </Button>
      </Dropdown>
    );
  }

  updateState = (props) => {
    props = props || this.props;
    if (props.value && props.value.length) {
      this.onSelectGroup(props.value);
    } else if (props.groups && (props.groups || []).length > 0) {
      const [privateGroup] = props.groups.filter(g => g.privateGroup);
      if (privateGroup) {
        this.onSelectGroup(privateGroup.name);
      } else {
        this.onSelectGroup(props.groups[0].name);
      }
    } else {
      this.onSelectGroup(null);
    }
  };

  componentWillReceiveProps (nextProps) {
    const groupsAreEqual = (group1, group2) => group1.name === group2.name;
    if (this.props.value !== nextProps.value ||
      !compareArrays(this.props.groups, nextProps.groups, groupsAreEqual)) {
      this.updateState(nextProps);
    }
  }

  componentDidUpdate () {
    if ((this.props.groups || []).length > 0 &&
      !this.state.value) {
      this.updateState();
    }
  }

  componentDidMount () {
    this.updateState();
  }
}
