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
import {observer} from 'mobx-react';
import {Row, Menu} from 'antd';
import AdaptedLink from '../special/AdaptedLink';
import styles from './Cluster.css';
import roleModel from '../../utils/roleModel';
import 'highlight.js/styles/github.css';

const Tabs = [
  {
    key: 'default',
    path: '/cluster',
    title: 'Cluster',
    available: () => true
  },
  {
    key: 'hot',
    path: '/cluster/hot',
    title: 'Hot Node Pools',
    available: (user) => user ? user.admin : false
  }
];

@roleModel.authenticationInfo
@observer
export default class extends React.Component {
  get currentUser () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value
      : undefined;
  };

  renderClusterNavigation = () => {
    const {router: {location}} = this.props;
    const tabs = Tabs.filter(tab => tab.available(this.currentUser));
    if (tabs.length < 2) {
      return null;
    }
    const activeTab = location.pathname.split('/').filter(Boolean)[1] || 'default';
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
                  location={location}
                  ignoreCurrentPath
                >
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
        {this.renderClusterNavigation()}
        {children}
      </div>
    );
  };
}
