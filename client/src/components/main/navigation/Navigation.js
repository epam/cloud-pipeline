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
import {Link} from 'react-router';
import {SERVER, VERSION} from '../../../config';
import {Button, Icon, message, Popover, Row, Tooltip} from 'antd';
import styles from './Navigation.css';
import PropTypes from 'prop-types';
import PipelineRunInfo from '../../../models/pipelines/PipelineRunInfo';
import RunsCounterMenuItem from './RunsCounterMenuItem';
import SettingsForm from './SettingsForm';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import searchStyles from '../../search/search.css';

export default class Navigation extends React.Component {
  static propTypes = {
    router: PropTypes.object,
    onLibraryCollapsedChange: PropTypes.func,
    collapsed: PropTypes.bool,
    activeTabPath: PropTypes.string,
    deploymentName: PropTypes.string,
    openSearchDialog: PropTypes.func,
    searchControlVisible: PropTypes.bool
  };

  state = {
    settingsControlVisible: false,
    versionInfoVisible: false
  };

  navigationItems = [
    {
      title: 'Home',
      icon: 'home',
      path: '/',
      key: 'home',
      keys: ['home', ''],
      isDefault: false,
      isLink: true
    },
    {
      title: 'Library',
      icon: 'fork',
      path: '/library',
      key: 'pipelines',
      isDefault: true,
      isLink: true
    },
    {
      title: 'Cluster state',
      icon: 'bars',
      path: '/cluster',
      key: 'cluster',
      isDefault: false,
      isLink: true
    },
    {
      title: 'Tools',
      icon: 'tool',
      path: '/tools',
      key: 'tools',
      keys: ['tools', 'tool'],
      isDefault: false,
      isLink: true
    },
    {
      title: 'Runs',
      icon: 'play-circle',
      path: '/runs',
      key: 'runs',
      isDefault: false,
      isLink: true
    },
    {
      title: 'Settings',
      icon: 'setting',
      path: '/settings',
      key: 'settings',
      isDefault: false
    },
    {
      title: 'Search results',
      icon: 'search',
      path: '/search',
      key: 'search',
      isDefault: false
    },
    {
      key: 'divider',
      isDivider: true
    },
    {
      title: 'Log out',
      icon: 'poweroff',
      path: '/logout',
      key: 'logout',
      isDefault: false
    }
  ];

  menuItemClassSelector = (navigationItem, activeItem) => {
    if (navigationItem.key.toLowerCase() === activeItem.toLowerCase()) {
      return styles.navigationMenuItemSelected;
    } else {
      return styles.navigationMenuItem;
    }
  };

  highlightedMenuItemClassSelector = (navigationItem, activeItem) => {
    if (navigationItem.key.toLowerCase() === activeItem.toLowerCase()) {
      return styles.highlightedNavigationMenuItemSelected;
    } else {
      return styles.highlightedNavigationMenuItem;
    }
  };

  navigate = ({key}) => {
    if (key === 'search') {
      this.props.openSearchDialog && this.props.openSearchDialog();
    } else if (key === 'settings') {
      this.openSettingsForm();
    } else if (key === 'runs') {
      SessionStorageWrapper.navigateToActiveRuns(this.props.router);
    } else if (key === 'logout') {
      let url = `${SERVER}/saml/logout`;
      if (SERVER.endsWith('/')) {
        url = `${SERVER}saml/logout`;
      }
      window.location = url;
    }
  };

  openSettingsForm = () => {
    this.setState({settingsControlVisible: true});
  };

  closeSettingsForm = () => {
    this.setState({settingsControlVisible: false});
  };

  closeVersionInfoControl = () => {
    this.setState({versionInfoVisible: false});
  };

  handleVersionInfoVisible = (visible) => {
    this.setState({versionInfoVisible: visible});
  };

  async navigateToRun (runId) {
    const info = new PipelineRunInfo(runId);
    await info.fetch();
    if (info.error) {
      message.error(info.error, 5);
    } else {
      message.destroy();
      this.props.router.push(`/run/${runId}`);
    }
  }

  render () {
    let activeTabPath = this.props.activeTabPath || '';
    const [navigationItem] = this.navigationItems.filter(
      item => item.key.toLowerCase() === activeTabPath || (item.keys && item.keys.indexOf(activeTabPath) >= 0)
    );
    if (navigationItem) {
      activeTabPath = navigationItem.key;
    }
    if (!navigationItem && activeTabPath.toLowerCase() !== 'run' &&
      activeTabPath.toLowerCase() !== 'launch') {
      const activeTab = this.navigationItems.filter(item => item.isDefault)[0];
      if (activeTab) {
        activeTabPath = activeTab.key;
      }
    }
    const menuItems = this.navigationItems.map((navigationItem, index) => {
      if (navigationItem.isDivider) {
        return <div
          key={`divider_${index}`}
          style={{height: 1, width: '100%', backgroundColor: '#fff', opacity: 0.5}} />;
      }
      if (navigationItem.key === 'search') {
        return (
          <Button
            id={`navigation-button-${navigationItem.key}`}
            key={navigationItem.key}
            className={this.menuItemClassSelector(navigationItem, activeTabPath)}
            onClick={() => this.navigate({key: navigationItem.key})}
          >
            <Icon type={navigationItem.icon} />
          </Button>
        );
      } else if (navigationItem.key === 'runs') {
        return (
          <Tooltip
            key={navigationItem.key}
            placement="right"
            text={navigationItem.title}
            mouseEnterDelay={0.5}
            overlay={navigationItem.title}>
            <RunsCounterMenuItem
              className={this.menuItemClassSelector(navigationItem, activeTabPath)}
              highlightedClassName={this.highlightedMenuItemClassSelector(
                navigationItem,
                activeTabPath
              )}
              onClick={() => this.navigate({key: navigationItem.key})}
              icon={navigationItem.icon} />
          </Tooltip>
        );
      } else if (navigationItem.isLink) {
        return (
          <Link
            id={`navigation-button-${navigationItem.key}`}
            key={navigationItem.key}
            style={{display: 'block', margin: '0 2px', textDecoration: 'none'}}
            className={this.menuItemClassSelector(navigationItem, activeTabPath)}
            to={navigationItem.path}>
            <Tooltip
              placement="right"
              text={navigationItem.title}
              mouseEnterDelay={0.5}
              overlay={navigationItem.title}>
              <Icon
                style={{marginTop: 12}}
                type={navigationItem.icon} />
            </Tooltip>
          </Link>
        );
      } else {
        return (
          <Tooltip
            key={navigationItem.key}
            placement="right"
            text={navigationItem.title}
            mouseEnterDelay={0.5}
            overlay={navigationItem.title}>
            <Button
              id={`navigation-button-${navigationItem.key}`}
              key={navigationItem.key}
              className={this.menuItemClassSelector(navigationItem, activeTabPath)}
              onClick={() => this.navigate({key: navigationItem.key})}
            >
              <Icon type={navigationItem.icon} />
            </Button>
          </Tooltip>
        );
      }
    });
    const searchStyle = [searchStyles.searchBlur];
    if (this.props.searchControlVisible) {
      searchStyle.push(searchStyles.enabled);
    }
    return (
      <div
        id="navigation-container"
        className={styles.navigationContainer}>
        <div className={`${styles.navigationInsideContainer} ${searchStyle.join(' ')}`}>
          {
            VERSION &&
            <Popover
              content={
                <Row>
                  <Row>
                    <b>{this.props.deploymentName || 'EPAM Cloud Pipeline'}</b>
                  </Row>
                  <Row>
                    <b>Version:</b> {VERSION}
                  </Row>
                </Row>
              }
              placement="right"
              trigger="click"
              onVisibleChange={this.handleVersionInfoVisible}
              visible={this.state.versionInfoVisible}>
              <Button
                id="navigation-button-logo"
                className={styles.logoMenuItem}>
                <img src="favicon.png" style={{width: 26, height: 26}} />
              </Button>
            </Popover>
          }
          {menuItems}
          {
            activeTabPath === 'pipelines' &&
            <Button
              id="expand-collapse-library-tree-button"
              onClick={this.props.onLibraryCollapsedChange}
              className={styles.navigationMenuItem}
              style={{position: 'absolute', left: 0, bottom: 0, right: 0}}>
              <Icon type={this.props.collapsed ? 'right' : 'left'} />
            </Button>
          }
        </div>
        <SettingsForm
          visible={this.state.settingsControlVisible}
          onClose={this.closeSettingsForm}
        />
      </div>
    );
  }
}
