/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {Button, Icon, Input} from 'antd';
import Menu, {MenuItem, SubMenu} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import classNames from 'classnames';
import allModules from '../model/modules';
import styles from './cell-profiler.css';

class AddModulesButton extends React.Component {
  state = {
    addModuleSelectorVisible: false,
    openedKeys: [],
    filter: undefined
  };

  render () {
    const {analysis} = this.props;
    if (!analysis) {
      return null;
    }
    const handleVisibility = (visible) => this.setState({
      addModuleSelectorVisible: visible,
      openedKeys: [],
      filter: undefined
    });
    const {filter} = this.state;
    const onChangeFilter = e => this.setState({filter: e.target.value});
    const filtered = allModules
      .filter(module => !module.hidden)
      .filter(module => !filter ||
        (module.name || '').toLowerCase().includes(filter.toLowerCase())
      );
    const onSelect = ({key}) => {
      const cpModule = filtered.find((cpModule) => cpModule.name === key);
      if (analysis) {
        analysis.add(cpModule);
      }
      handleVisibility(false);
    };
    const onOpenChange = keys => this.setState({openedKeys: keys});
    const groups = filter
      ? []
      : [...(new Set(filtered.map(module => module.group)))].filter(Boolean);
    const rootLevelModules = filtered
      .filter(module => !module.group || !groups.includes(module.group));
    const menu = (
      <div
        className={
          classNames(
            styles.modulesDropdownContainer,
            'cp-panel'
          )
        }
      >
        <div
          className={styles.modulesFilter}
        >
          <Input
            value={filter}
            onChange={onChangeFilter}
            style={{width: '100%'}}
            placeholder="Search modules"
          />
        </div>
        {
          filtered.length === 0 && (
            <div
              className={
                classNames(
                  styles.modulesDropdown,
                  'cp-text-not-important'
                )
              }
              style={{
                padding: 5,
                lineHeight: '24px'
              }}
            >
              Nothing found
            </div>
          )
        }
        <div
          className={styles.modulesDropdown}
        >
          <Menu
            openKeys={this.state.openedKeys}
            selectedKeys={[]}
            onClick={onSelect}
            onOpenChange={onOpenChange}
            style={{border: 'none'}}
          >
            {
              rootLevelModules
                .map((cpModule) => (
                  <MenuItem key={cpModule.name}>
                    {cpModule.title || cpModule.name}
                  </MenuItem>
                ))
            }
            {
              groups.map((group) => (
                <SubMenu
                  key={group}
                  title={group}
                  selectedKeys={[]}
                >
                  {
                    filtered
                      .filter(module => module.group === group)
                      .map((cpModule) => (
                        <MenuItem key={cpModule.name}>
                          {cpModule.title || cpModule.name}
                        </MenuItem>
                      ))
                  }
                </SubMenu>
              ))
            }
          </Menu>
        </div>
      </div>
    );
    return (
      <Dropdown
        overlay={menu}
        trigger={['click']}
        onVisibleChange={handleVisibility}
        visible={this.state.addModuleSelectorVisible}
        getPopupContainer={node => node.parentNode}
      >
        <Button
          size="small"
        >
          <Icon type="plus" />
          <span>Add module</span>
        </Button>
      </Dropdown>
    );
  };
}

AddModulesButton.propTypes = {
  analysis: PropTypes.object
};

export default observer(AddModulesButton);
