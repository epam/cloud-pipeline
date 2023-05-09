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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {SERVER} from '../../../config';
import {Button, Icon, message, Popover, Tooltip} from 'antd';
import PropTypes from 'prop-types';
import PipelineRunInfo from '../../../models/pipelines/PipelineRunInfo';
import CounterMenuItem from './CounterMenuItem';
import SupportMenu from './support-menu';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import searchStyles from '../../search/search.css';
import invalidateEdgeTokens from '../../../utils/invalidate-edge-tokens';
import ApplicationVersion from './application-version';
import RunsFilterDescription from '../../runs/run-table/runs-filter-description';

@inject(
  'impersonation',
  'preferences',
  'counter',
  'userNotifications'
)
@observer
export default class Navigation extends React.Component {
  static propTypes = {
    router: PropTypes.object,
    onLibraryCollapsedChange: PropTypes.func,
    collapsed: PropTypes.bool,
    activeTabPath: PropTypes.string,
    deploymentName: PropTypes.string,
    openSearchDialog: PropTypes.func,
    searchControlVisible: PropTypes.bool,
    searchEnabled: PropTypes.bool,
    billingEnabled: PropTypes.bool
  };

  state = {
    versionInfoVisible: false
  };

  @computed
  get notificationsEnabled () {
    const {preferences} = this.props;
    if (preferences.loaded) {
      return preferences.userNotificationsEnabled;
    }
    return false;
  }

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
      keys: [
        'settings',
        'cli',
        'events',
        'user',
        'email',
        'preferences',
        'regions',
        'logs',
        'dictionaries'
      ],
      isDefault: false,
      isLink: true
    },
    {
      title: 'Search',
      icon: 'search',
      path: '/search',
      key: 'search',
      isDefault: false
    },
    {
      title: 'Billing',
      icon: 'area-chart',
      path: '/billing',
      key: 'billing',
      isDefault: false,
      isLink: true
    },
    {
      key: 'notifications',
      title: 'Notifications',
      icon: 'mail',
      path: '/notifications',
      isDefault: false,
      isLink: true
    },
    {
      key: 'divider',
      isDivider: true
    },
    {
      key: 'logout',
      visible: props => !(props && props.impersonation && props.impersonation.isImpersonated),
      title: 'Log out',
      icon: 'poweroff',
      path: '/logout',
      isDefault: false,
      static: true
    },
    {
      key: 'stop-impersonation',
      visible: props => props && props.impersonation && props.impersonation.isImpersonated,
      title: (props) => props && props.impersonation && props.impersonation.isImpersonated
        ? `Stop impersonation as ${props.impersonation.impersonatedUserName}`
        : undefined,
      icon: 'user-delete',
      isDefault: false,
      static: true,
      action: (props) => props && props.impersonation
        ? props.impersonation.stopImpersonation()
        : undefined
    }
  ];

  @computed
  get runsCount () {
    const {counter} = this.props;
    if (counter && counter.loaded) {
      return counter.runsCount || 0;
    }
    return 0;
  }

  @computed
  get notificationsCount () {
    const {userNotifications} = this.props;
    if (userNotifications && userNotifications.loaded) {
      return userNotifications.value.totalCount || 0;
    }
    return 0;
  }

  menuItemClassSelector = (navigationItem, activeItem) => {
    return classNames(
      'cp-navigation-menu-item',
      {
        'selected': navigationItem.key === activeItem
      }
    );
  };

  highlightedMenuItemClassSelector = (navigationItem, activeItem) => {
    return classNames(
      'cp-navigation-menu-item',
      'cp-runs-menu-item',
      {
        'selected': navigationItem.key === activeItem
      }
    );
  };

  navigate = (navigationItem) => {
    const {key} = navigationItem;
    if (key === 'search') {
      this.props.openSearchDialog && this.props.openSearchDialog();
    } else if (key === 'runs') {
      SessionStorageWrapper.navigateToActiveRuns(this.props.router);
    } else if (key === 'logout') {
      invalidateEdgeTokens()
        .then(() => {
          let url = `${SERVER}/saml/logout`;
          if (SERVER.endsWith('/')) {
            url = `${SERVER}saml/logout`;
          }
          window.location = url;
        });
    } else if (typeof navigationItem.action === 'function') {
      navigationItem.action(this.props);
    } else if (navigationItem.isLink && typeof navigationItem.path === 'string') {
      this.props.router.push(navigationItem.path);
    }
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

  getNavigationItemTitle = (title) => {
    if (typeof title === 'function') {
      return title(this.props, this.state);
    }
    return title;
  };

  getNavigationItemVisible = (navigationItem) => {
    if (typeof navigationItem.visible === 'function') {
      return navigationItem.visible(this.props, this.state);
    }
    if (navigationItem.visible === undefined) {
      return true;
    }
    return !!navigationItem.visible;
  };

  render () {
    let activeTabPath = this.props.activeTabPath || '';
    const [navigationItem] = this.navigationItems.filter(
      item => item.key.toLowerCase() === activeTabPath ||
        (item.keys && item.keys.indexOf(activeTabPath) >= 0)
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
    const {impersonation, counter} = this.props;
    const menuItems = this.navigationItems
      .filter(item => this.getNavigationItemVisible(item))
      .map((navigationItem, index) => {
        if (navigationItem.isDivider) {
          return (
            <div
              className={
                classNames(
                  'cp-divider',
                  'horizontal',
                  'cp-navigation-divider'
                )
              }
              key={`divider_${index}`}
            />
          );
        }
        if (navigationItem.key === 'billing' && !this.props.billingEnabled) {
          return null;
        }
        if (navigationItem.key === 'search') {
          if (!this.props.searchEnabled) {
            return null;
          }
          return (
            <Tooltip
              key={navigationItem.key}
              placement="right"
              text={this.getNavigationItemTitle(navigationItem.title)}
              mouseEnterDelay={0.5}
              overlay={this.getNavigationItemTitle(navigationItem.title)}>
              <Button
                id={`navigation-button-${navigationItem.key}`}
                key={navigationItem.key}
                className={this.menuItemClassSelector(navigationItem, activeTabPath)}
                onClick={() => this.navigate({key: navigationItem.key})}
              >
                <Icon
                  style={navigationItem.iconStyle}
                  type={navigationItem.icon}
                />
              </Button>
            </Tooltip>
          );
        }
        if (navigationItem.key === 'runs') {
          return (
            <CounterMenuItem
              key={navigationItem.key}
              id={`navigation-button-${navigationItem.key}`}
              tooltip={<RunsFilterDescription filters={counter} />}
              className={
                classNames(
                  this.menuItemClassSelector(navigationItem, activeTabPath),
                  'cp-runs-menu-item',
                  {
                    active: this.runsCount > 0
                  }
                )
              }
              onClick={() => this.navigate(navigationItem)}
              icon={navigationItem.icon}
              count={this.runsCount}
            />
          );
        }
        if (navigationItem.key === 'notifications') {
          return this.notificationsEnabled ? (
            <CounterMenuItem
              key={navigationItem.key}
              id={`navigation-button-${navigationItem.key}`}
              tooltip={this.getNavigationItemTitle(navigationItem.title)}
              className={this.menuItemClassSelector(navigationItem, activeTabPath)}
              onClick={() => this.navigate(navigationItem)}
              icon={navigationItem.icon}
              count={this.notificationsCount}
            />
          ) : null;
        }
        if (navigationItem.isLink) {
          return (
            <Link
              id={`navigation-button-${navigationItem.key}`}
              key={navigationItem.key}
              className={this.menuItemClassSelector(navigationItem, activeTabPath)}
              to={navigationItem.path}>
              <Tooltip
                placement="right"
                text={this.getNavigationItemTitle(navigationItem.title)}
                mouseEnterDelay={0.5}
                overlay={this.getNavigationItemTitle(navigationItem.title)}>
                <Icon
                  style={navigationItem.iconStyle}
                  type={navigationItem.icon}
                />
              </Tooltip>
            </Link>
          );
        }
        return (
          <Tooltip
            key={navigationItem.key}
            placement="right"
            text={this.getNavigationItemTitle(navigationItem.title)}
            mouseEnterDelay={0.5}
            overlay={this.getNavigationItemTitle(navigationItem.title)}>
            <Button
              id={`navigation-button-${navigationItem.key}`}
              key={navigationItem.key}
              className={this.menuItemClassSelector(navigationItem, activeTabPath)}
              onClick={() => this.navigate(navigationItem)}
            >
              <Icon
                style={navigationItem.iconStyle}
                type={navigationItem.icon}
              />
            </Button>
          </Tooltip>
        );
      })
      .filter(Boolean);
    return (
      <div
        id="navigation-container"
        className={
          classNames(
            'cp-navigation-panel',
            {
              impersonated: impersonation.isImpersonated
            }
          )
        }
        style={{
          height: '100vh'
        }}
      >
        <div
          className={
            classNames(
              searchStyles.searchBlur,
              {
                [searchStyles.enabled]: this.props.searchControlVisible
              }
            )
          }
        >
          <Popover
            content={
              <ApplicationVersion />
            }
            placement="right"
            trigger="click"
            onVisibleChange={this.handleVersionInfoVisible}
            visible={this.state.versionInfoVisible}>
            <Button
              id="navigation-button-logo"
              className="cp-navigation-menu-item">
              <div className="cp-navigation-item-logo">
                {'\u00A0'}
              </div>
            </Button>
          </Popover>
          {menuItems}
          <SupportMenu
            itemClassName="cp-navigation-menu-item"
            containerStyle={{
              position: 'absolute',
              left: 0,
              bottom: activeTabPath === 'pipelines' ? 44 : 10,
              right: 0
            }}
          />
          {
            activeTabPath === 'pipelines' &&
            <Button
              id="expand-collapse-library-tree-button"
              onClick={this.props.onLibraryCollapsedChange}
              className="cp-navigation-menu-item"
              style={{position: 'absolute', left: 0, bottom: 0, right: 0}}
            >
              <Icon type={this.props.collapsed ? 'right' : 'left'} />
            </Button>
          }
        </div>
      </div>
    );
  }
}
