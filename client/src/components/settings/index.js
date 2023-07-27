/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {Row, Menu} from 'antd';
import classNames from 'classnames';
import PipelineGitCredentials from '../../models/pipelines/PipelineGitCredentials';
import AdaptedLink from '../special/AdaptedLink';
import styles from './styles.css';
import roleModel from '../../utils/roleModel';
import 'highlight.js/styles/github.css';

const SettingsTabs = [
  {
    key: 'cli',
    path: '/settings/cli',
    title: 'CLI',
    available: () => true
  },
  {
    key: 'events',
    path: '/settings/events',
    title: 'System events',
    available: (user) => user ? user.admin : false
  },
  {
    key: 'user',
    path: '/settings/user',
    title: 'User management',
    available: (user, props) => {
      if (!user) {
        return false;
      }
      if (roleModel.userHasRole(user, 'ROLE_USER_READER')) {
        return true;
      }
      const {users} = props || {};
      if (users.pending && !users.loaded) {
        return false;
      }
      const list = users.value || [];
      return list.some((user) => roleModel.readAllowed(user));
    }
  },
  {
    key: 'email',
    path: '/settings/email',
    title: 'Email notifications',
    available: (user) => user ? user.admin : false
  },
  {
    key: 'preferences',
    path: '/settings/preferences',
    title: 'Preferences',
    available: (user) => user ? user.admin : false
  },
  {
    key: 'regions',
    path: '/settings/regions',
    title: 'Cloud regions',
    available: (user) => user ? user.admin : false
  },
  {
    key: 'dictionaries',
    path: '/settings/dictionaries',
    title: 'System Dictionaries',
    available: (user) => user ? user.admin : false
  },
  {
    key: 'system',
    path: '/settings/system',
    title: 'System Management',
    available: (user) => user ? user.admin : false
  },
  {
    key: 'profile',
    path: '/settings/profile',
    title: 'My Profile',
    available: () => true
  }
];

@inject(() => ({
  pipelineGitCredentials: new PipelineGitCredentials()
}))
@inject('users')
@roleModel.authenticationInfo
@observer
export default class extends React.Component {
  get currentUser () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value
      : undefined;
  };

  renderSettingsNavigation = () => {
    const {router: {location}} = this.props;
    const tabs = SettingsTabs.filter(tab => tab.available(this.currentUser, this.props));
    const activeTab = location.pathname.split('/').filter(Boolean)[1];
    return (
      <Row
        gutter={16}
        type="flex"
        justify="center"
        className={styles.rowMenu}
      >
        <Menu
          mode="horizontal"
          selectedKeys={[activeTab]}
          className={styles.tabsMenu}
        >
          {
            tabs.map(tab => (
              <Menu.Item key={tab.key}>
                <AdaptedLink
                  to={tab.path}
                  location={location}>
                  {tab.title}
                </AdaptedLink>
              </Menu.Item>
            ))
          }
        </Menu>
      </Row>
    );
  };

  render () {
    const {children} = this.props;

    return (
      <div
        className={
          classNames(
            styles.container,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
      >
        {this.renderSettingsNavigation()}
        {children}
      </div>
    );
  };
}
