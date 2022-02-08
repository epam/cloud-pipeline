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
import {SERVER, VERSION} from '../../../config';
import {Button, Icon, message, Popover, Row, Tooltip} from 'antd';
import PropTypes from 'prop-types';
import PipelineRunInfo from '../../../models/pipelines/PipelineRunInfo';
import RunsCounterMenuItem from './RunsCounterMenuItem';
import SupportMenu from './support-menu';
import SessionStorageWrapper from '../../special/SessionStorageWrapper';
import searchStyles from '../../search/search.css';
import {Pages} from '../../../utils/ui-navigation';
import invalidateEdgeTokens from '../../../utils/invalidate-edge-tokens';

@inject('uiNavigation', 'impersonation')
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
  get navigationItems () {
    const {uiNavigation} = this.props;
    return uiNavigation.navigationItems
      .filter(item => !item.hidden);
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

  navigate = ({key}) => {
    if (key === 'runs') {
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
    } else {
      const item = this.navigationItems.find(item => item.key === key);
      if (item && typeof item.action === 'function') {
        item.action(this.props);
      }
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
    const {activeTabPath, impersonation} = this.props;
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
        } else if (navigationItem.key === 'runs') {
          return (
            <RunsCounterMenuItem
              key={navigationItem.key}
              tooltip={this.getNavigationItemTitle(navigationItem.title)}
              className={this.menuItemClassSelector(navigationItem, activeTabPath)}
              onClick={() => this.navigate({key: navigationItem.key})}
              icon={navigationItem.icon} />
          );
        } else if (navigationItem.isLink) {
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
        } else {
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
                className="cp-navigation-menu-item">
                <div className="cp-navigation-item-logo">
                  {'\u00A0'}
                </div>
              </Button>
            </Popover>
          }
          {menuItems}
          <SupportMenu
            itemClassName="cp-navigation-menu-item"
            containerStyle={{
              position: 'absolute',
              left: 0,
              bottom: activeTabPath === Pages.library ? 44 : 10,
              right: 0
            }}
          />
          {
            activeTabPath === Pages.library &&
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
