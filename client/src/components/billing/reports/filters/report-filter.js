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
import {Menu} from 'antd';
import classNames from 'classnames';

import styles from './report-filter.css';

export default function ({onChange, filter}) {
  const onSelect = ({key}) => {
    onChange && onChange(key);
  };
  const isSubMenuSelected = (t) => (t === filter);
  const storagesMenu = (
    <Menu.SubMenu
      className={classNames(styles.styledSubMenu, {
        [styles.styledSubMenuSelected]: isSubMenuSelected('storages')
      })}
      key="storages"
      title="Storages"
      onTitleClick={onSelect}
    >
      <Menu.Item key="storages.file">File storages</Menu.Item>
      <Menu.Item key="storages.object">Object storages</Menu.Item>
    </Menu.SubMenu>
  );
  const instancesMenu = (
    <Menu.SubMenu
      className={classNames(styles.styledSubMenu, {
        [styles.styledSubMenuSelected]: isSubMenuSelected('instances')
      })}
      key="instances"
      title="Compute instances"
      onTitleClick={onSelect}
    >
      <Menu.Item key="instances.cpu">CPU</Menu.Item>
      <Menu.Item key="instances.gpu">GPU</Menu.Item>
    </Menu.SubMenu>
  );
  return (
    <Menu
      className={styles.styledMenu}
      mode="inline"
      inlineIndent={12}
      onClick={onSelect}
      openKeys={['storages', 'instances']}
      selectedKeys={[filter]}
    >
      <Menu.Item key="general">General</Menu.Item>
      {storagesMenu}
      {instancesMenu}
    </Menu>
  );
}
