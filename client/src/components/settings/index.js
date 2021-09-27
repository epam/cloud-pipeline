/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {withRouter} from 'react-router-dom';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Row, Menu} from 'antd';
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
    available: (user) => user ? roleModel.userHasRole(user, 'ROLE_USER_READER') : false
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
    key: 'logs',
    path: '/settings/logs',
    title: 'System Logs',
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
@roleModel.authenticationInfo
@observer
class SettingsForm extends React.Component {
  @computed
  get currentUser () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value
      : undefined;
  }

  @computed
  get tabs () {
    return SettingsTabs.filter(tab => tab.available(this.currentUser));
  }

  renderSettingsNavigation = () => {
    const {location, match} = this.props;
    // const tabs = SettingsTabs.filter(tab => tab.available(this.currentUser));
    const activeTab = match.params.activeTab;
    return (
      <Row
        type="flex"
        className={styles.rowMenu}
      >
        <Menu
          mode="horizontal"
          disabledOverflow
          selectedKeys={[activeTab]}
          className={styles.tabsMenu}
        >
          {
            this.tabs.map(tab => (
              <Menu.Item
                key={tab.key}
                className={styles.menuItem}
              >
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
      <div className={styles.container}>
        {this.renderSettingsNavigation()}
        {children}
      </div>
    );
  };
}

export default withRouter(SettingsForm);
