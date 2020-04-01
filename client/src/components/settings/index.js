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
import PipelineGitCredentials from '../../models/pipelines/PipelineGitCredentials';
import AdaptedLink from '../special/AdaptedLink';
import styles from './styles.css';
import 'highlight.js/styles/github.css';

@inject(() => ({
  pipelineGitCredentials: new PipelineGitCredentials()
}))
@observer
export default class Index extends React.Component {
  renderSettingsNavigation = () => {
    const {router: {location}} = this.props;
    const activeTab = location.pathname.split('/').pop();
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
          <Menu.Item key="cli">
            <AdaptedLink
              to={`/settings/cli`}
              location={location}>
              CLI
            </AdaptedLink>
          </Menu.Item>
          <Menu.Item key="events">
            <AdaptedLink
              to={`/settings/events`}
              location={location}>
              System events
            </AdaptedLink>
          </Menu.Item>
          <Menu.Item key="user">
            <AdaptedLink
              to={`/settings/user`}
              location={location}>
              User management
            </AdaptedLink>
          </Menu.Item>
          <Menu.Item key="email">
            <AdaptedLink
              to={`/settings/email`}
              location={location}>
              Email notifications
            </AdaptedLink>
          </Menu.Item>
          <Menu.Item key="preferences">
            <AdaptedLink
              to={`/settings/preferences`}
              location={location}>
              Preferences
            </AdaptedLink>
          </Menu.Item>
          <Menu.Item key="regions">
            <AdaptedLink
              to={`/settings/regions`}
              location={location}>
              Cloud Regions
            </AdaptedLink>
          </Menu.Item>
          <Menu.Item key="logs">
            <AdaptedLink
              to={`/settings/logs`}
              location={location}>
              System logs
            </AdaptedLink>
          </Menu.Item>
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
