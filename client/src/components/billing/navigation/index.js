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
import PropTypes from 'prop-types';
import {observable} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import classNames from 'classnames';
import {Menu} from 'antd';
import FilterStore from './filter-store';
import RunnerTypes from './runner-types';
import ReportsRouting from './reports-routing';
import styles from './billing-navigation.css';
import roleModel from '../../../utils/roleModel';

@roleModel.authenticationInfo
@observer
class BillingNavigation extends React.Component {
  static attach = (...opts) => inject('filters')(...opts);
  static generateNavigationFn = (navigation, ...configurationRest) => (
    (navigation)
      ? (...opts) => navigation(...configurationRest, ...opts)
      : undefined
  );
  static resourcesNavigation = (filters, {group: resourceGroup, key}) => {
    if (filters && resourceGroup) {
      if (/^storage$/i.test(resourceGroup)) {
        if (key && /^file$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.storages.file.name);
        } else if (key && /^object$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.storages.object.name);
        } else {
          filters.reportNavigation(ReportsRouting.storages.name);
        }
      } else if (/^compute instances$/i.test(resourceGroup)) {
        if (key && /^cpu$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.instances.cpu.name);
        } else if (key && /^gpu$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.instances.gpu.name);
        } else {
          filters.reportNavigation(ReportsRouting.instances.name);
        }
      }
    }
  };
  static usersNavigation = (filters, {key}) => {
    if (filters) {
      filters.reportNavigation(
        ReportsRouting.general.name,
        {id: key, type: RunnerTypes.user}
      );
    }
  };
  static billingCentersNavigation = (filters, {key}) => {
    if (filters) {
      filters.reportNavigation(
        ReportsRouting.general.name,
        {id: key, type: RunnerTypes.billingGroup}
      );
    }
  };

  @observable filterStore = new FilterStore();
  componentWillReceiveProps (nextProps, nextContext) {
    this.filterStore.rebuild(this.props);
  }

  componentDidMount () {
    this.filterStore.rebuild(this.props);
  }

  componentDidUpdate (prevProps) {
    const {location} = this.props;
    if (location) {
      const {pathname, search} = location;
      const {pathname: prevPathname, search: prevSearch} = prevProps.location;
      if (prevSearch !== search || prevPathname !== pathname) {
        this.filterStore.rebuild(this.props);
      }
    }
  }

  renderMenu = () => {
    const {
      buildNavigationFn
    } = this.filterStore || {};
    const {
      location
    } = this.props;
    const report = location
      ? ReportsRouting.parse(location)
      : undefined;
    const onSelect = ({key}) => {
      const onChange = buildNavigationFn('report');
      onChange && onChange(key);
    };
    const isSubMenuSelected = (t) => (t === report);
    const storagesMenu = (
      <Menu.SubMenu
        className={
          classNames(
            'cp-billing-sub-menu',
            {
              'cp-billing-sub-menu-selected': isSubMenuSelected('storages')
            }
          )
        }
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
        className={
          classNames(
            'cp-billing-sub-menu',
            {
              'cp-billing-sub-menu-selected': isSubMenuSelected('instances')
            }
          )
        }
        key="instances"
        title="Compute instances"
        onTitleClick={onSelect}
      >
        <Menu.Item key="instances.cpu">CPU</Menu.Item>
        <Menu.Item key="instances.gpu">GPU</Menu.Item>
      </Menu.SubMenu>
    );
    const quotasMenu = (
      <Menu.SubMenu
        className={
          classNames(
            'cp-billing-sub-menu',
            {
              'cp-billing-sub-menu-selected': isSubMenuSelected('quotas')
            }
          )
        }
        key="quotas"
        title="Quotas"
        onTitleClick={onSelect}
      >
        <Menu.Item key="quotas.storage">Storages</Menu.Item>
        <Menu.Item key="quotas.compute">Compute instances</Menu.Item>
      </Menu.SubMenu>
    );
    const isBillingManager = roleModel.isManager.billing(this);
    return (
      <Menu
        className="cp-billing-menu"
        mode="inline"
        inlineIndent={12}
        onClick={onSelect}
        openKeys={['storages', 'instances', 'quotas']}
        selectedKeys={[report]}
      >
        <Menu.Item key="general">General</Menu.Item>
        {storagesMenu}
        {instancesMenu}
        {isBillingManager && <Menu.Divider />}
        {isBillingManager && quotasMenu}
      </Menu>
    );
  };

  render () {
    if (!this.filterStore) {
      return null;
    }
    const {
      children,
      className
    } = this.props;
    return (
      <Provider filters={this.filterStore}>
        <div
          className={
            classNames(
              styles.container,
              className
            )
          }
        >
          <div className={styles.menu}>
            {this.renderMenu()}
          </div>
          <div className={styles.content}>
            {children}
          </div>
        </div>
      </Provider>
    );
  }
}

BillingNavigation.propTypes = {
  className: PropTypes.string,
  location: PropTypes.object,
  router: PropTypes.object,
  children: PropTypes.node
};

const RUNNER_SEPARATOR = FilterStore.RUNNER_SEPARATOR;
const REGION_SEPARATOR = FilterStore.REGION_SEPARATOR;

export {RUNNER_SEPARATOR, REGION_SEPARATOR, RunnerTypes};

export default BillingNavigation;
